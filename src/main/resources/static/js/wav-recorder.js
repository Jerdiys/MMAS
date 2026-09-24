let audioContext;
let audioProcessor;
let audioChunks = [];
let stream;

async function startWavRecording() {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    audioContext = new AudioContext({ sampleRate: 16000 });
    const source = audioContext.createMediaStreamSource(stream);
    
    // 4096 sample buffers, 1 input channel, 1 output channel
    audioProcessor = audioContext.createScriptProcessor(4096, 1, 1);
    source.connect(audioProcessor);
    audioProcessor.connect(audioContext.destination);
    
    audioChunks = [];
    audioProcessor.onaudioprocess = (e) => {
        const inputData = e.inputBuffer.getChannelData(0);
        audioChunks.push(new Float32Array(inputData));
    };
}

async function stopWavRecording() {
    audioProcessor.disconnect();
    stream.getTracks().forEach(track => track.stop());
    audioContext.close();
    
    // Merge all recorded chunks
    let totalLength = audioChunks.reduce((acc, chunk) => acc + chunk.length, 0);
    let mergedSamples = new Float32Array(totalLength);
    let offset = 0;
    for (let chunk of audioChunks) {
        mergedSamples.set(chunk, offset);
        offset += chunk.length;
    }
    
    // Build WAV headers + 16-bit PCM conversion
    const buffer = new ArrayBuffer(44 + mergedSamples.length * 2);
    const view = new DataView(buffer);
    
    writeString(view, 0, 'RIFF');
    view.setUint32(4, 36 + mergedSamples.length * 2, true);
    writeString(view, 8, 'WAVE');
    writeString(view, 12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true); // PCM Format
    view.setUint16(22, 1, true); // Mono
    view.setUint32(24, 16000, true); // Sample Rate
    view.setUint32(28, 16000 * 2, true); // Byte Rate
    view.setUint16(32, 2, true); // Block Align
    view.setUint16(34, 16, true); // 16 bits per sample
    writeString(view, 36, 'data');
    view.setUint32(40, mergedSamples.length * 2, true);
    
    // Write sample values mapping [-1.0, 1.0] floats to signed 16-bit integers
    let index = 44;
    for (let i = 0; i < mergedSamples.length; i++) {
        let sampleVal = Math.max(-1.0, Math.min(1.0, mergedSamples[i]));
        view.setInt16(index, sampleVal < 0 ? sampleVal * 0x8000 : sampleVal * 0x7FFF, true);
        index += 2;
    }
    
    return new Blob([view], { type: 'audio/wav' });
}

function writeString(view, offset, string) {
    for (let i = 0; i < string.length; i++) {
        view.setUint8(offset + i, string.charCodeAt(i));
    }
}
