package com.mmas.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class FloatArrayConverter implements AttributeConverter<float[], String> {
    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null || attribute.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (float val : attribute) {
            sb.append(val).append(",");
        }
        return sb.substring(0, sb.length() - 1);
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) return new float[0];
        String[] tokens = dbData.split(",");
        float[] floats = new float[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            floats[i] = Float.parseFloat(tokens[i].trim());
        }
        return floats;
    }
}
