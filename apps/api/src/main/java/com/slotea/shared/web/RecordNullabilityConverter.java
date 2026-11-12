package com.slotea.shared.web;

import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Lleva la nulabilidad de los DTOs ({@code record}) al OpenAPI, y de ahí a los tipos de TypeScript:
 * un componente sin {@link Nullable} es obligatorio; uno con {@link Nullable} es opcional y admite
 * {@code null} (en una petición se puede omitir; en una respuesta puede venir a {@code null}).
 * Sin esto, springdoc marcaría todos los campos de las respuestas como opcionales.
 */
@Component
class RecordNullabilityConverter implements ModelConverter {

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public @Nullable Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema resolved = chain.next().resolve(type, context, chain);
        Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
        if (resolved == null || raw == null || !raw.isRecord()) {
            return resolved;
        }
        Schema model = resolved;
        if (resolved.get$ref() != null) {
            String name = resolved.get$ref().substring(resolved.get$ref().lastIndexOf('/') + 1);
            model = context.getDefinedModels().get(name);
        }
        if (model == null || model.getProperties() == null) {
            return resolved;
        }
        Map<String, Schema> properties = model.getProperties();
        for (RecordComponent component : raw.getRecordComponents()) {
            Schema property = properties.get(component.getName());
            if (property == null) {
                continue;
            }
            if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                properties.put(component.getName(), nullable(property));
            } else if (model.getRequired() == null || !model.getRequired().contains(component.getName())) {
                model.addRequiredItem(component.getName());
            }
        }
        return resolved;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Schema nullable(Schema property) {
        if (property.get$ref() != null) {
            return new Schema().anyOf(List.of(property, new Schema().types(Set.of("null"))));
        }
        if (property.getTypes() != null && !property.getTypes().contains("null")) {
            property.addType("null");
        }
        return property;
    }
}
