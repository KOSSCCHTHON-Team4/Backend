package team4.emotionmap.platform.openapi;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.error.ApiError;

/** Documents contracts that servlet parsing and conditional ResponseEntity statuses cannot infer. */
@Component
public class ApiContractCustomizer implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI api) {
        if (api.getComponents() == null) {
            api.setComponents(new Components());
        }
        ModelConverters.getInstance().read(ApiError.class).forEach(api.getComponents()::addSchemas);
        Schema<?> errorSchema = api.getComponents().getSchemas().get("ApiError");
        if (errorSchema != null) {
            errorSchema.setRequired(List.of("code", "message", "retryAfterSeconds", "fieldErrors", "requestId"));
            Schema<?> retry = new io.swagger.v3.oas.models.media.ComposedSchema()
                    .addAnyOfItem(new IntegerSchema().format("int32"))
                    .addAnyOfItem(nullSchema());
            errorSchema.addProperties("retryAfterSeconds", retry);
        }
        for (String request : List.of("LoginRequest", "OnboardingRequest", "PreferencesRequest",
                "MemoryCreateRequest", "AnalyzeRequest", "ReportCreateRequest")) {
            Schema<?> schema = api.getComponents().getSchemas().get(request);
            if (schema != null) {
                schema.setAdditionalProperties(false);
            }
        }
        nullableProperties(api, "OnboardingRequest", "preferenceDescription");
        nullableProperties(api, "PreferencesRequest", "preferenceDescription", "mailboxLat", "mailboxLng");
        nullableProperties(api, "UserResponse", "mailbox", "atmospheres", "preferenceDescription",
                "preferenceVersion", "preferenceEffectiveAt");
        Schema<?> user = api.getComponents().getSchemas().get("UserResponse");
        if (user != null && user.getProperties() != null) {
            user.setRequired(List.copyOf(user.getProperties().keySet()));
        }
        api.getComponents().getSchemas().values().forEach(rawSchema -> {
            Schema<?> schema = rawSchema;
            if (schema.getProperties() == null) {
                return;
            }
            schema.getProperties().replaceAll((name, value) -> {
                if (value.getTypes() == null || !value.getTypes().contains("null")
                        || (value.get$ref() == null && value.getEnum() == null)) {
                    return value;
                }
                // In JSON Schema, $ref and enum still constrain a sibling null type.
                Set<String> nonNullTypes = new HashSet<>(value.getTypes());
                nonNullTypes.remove("null");
                value.setTypes(nonNullTypes.isEmpty() ? null : nonNullTypes);
                if ("null".equals(value.getType())) {
                    value.setType(null);
                }
                return new io.swagger.v3.oas.models.media.ComposedSchema()
                        .addAnyOfItem(value).addAnyOfItem(nullSchema());
            });
        });
        if (api.getPaths() == null) {
            return;
        }
        api.getPaths().forEach((path, item) -> {
            if (!path.startsWith("/v1/")) {
                return;
            }
            item.readOperations().forEach(operation -> {
                if (operation.getResponses() == null) {
                    operation.setResponses(new ApiResponses());
                }
                operation.getResponses().addApiResponse("default", errorResponse());
            });
        });
        Operation login = post(api, "/v1/auth/login");
        if (login != null) {
            login.setSecurity(List.of());
        }
        for (String path : List.of("/v1/memories", "/v1/images", "/v1/reports")) {
            Operation operation = post(api, path);
            if (operation == null) {
                continue;
            }
            if (operation.getParameters() == null || operation.getParameters().stream().noneMatch(parameter ->
                    "header".equals(parameter.getIn()) && "Idempotency-Key".equalsIgnoreCase(parameter.getName()))) {
                operation.addParametersItem(new Parameter().name("Idempotency-Key").in("header").required(true)
                        .description("Exactly one UUID per submission. Reuse it only for retries of the same request.")
                        .schema(new StringSchema().format("uuid")));
            }
            ApiResponse success = operation.getResponses().get("200");
            if (success == null) {
                success = operation.getResponses().get("201");
            }
            if (success != null) {
                operation.getResponses().addApiResponse("201", new ApiResponse().description("Created")
                        .content(success.getContent()));
                operation.getResponses().addApiResponse("200", new ApiResponse().description("Existing submission replay")
                        .content(success.getContent()));
            }
        }
        Operation upload = post(api, "/v1/images");
        if (upload != null) {
            ObjectSchema multipart = new ObjectSchema();
            multipart.addProperties("file", new StringSchema().format("binary"));
            multipart.setRequired(List.of("file"));
            multipart.setAdditionalProperties(false);
            upload.setRequestBody(new RequestBody().required(true)
                    .description("Exactly one file part named file; no additional parts or query parameters.")
                    .content(new Content().addMediaType("multipart/form-data", new MediaType().schema(multipart))));
        }
        var imagePath = api.getPaths().get("/v1/memories/{id}/image");
        if (imagePath != null && imagePath.getGet() != null) {
            ApiResponse image = imagePath.getGet().getResponses().get("200");
            if (image != null) {
                image.setContent(new Content()
                        .addMediaType("image/jpeg", new MediaType().schema(new StringSchema().format("binary")))
                        .addMediaType("image/png", new MediaType().schema(new StringSchema().format("binary"))));
            }
        }
    }

    private static Operation post(OpenAPI api, String path) {
        var item = api.getPaths().get(path);
        return item == null ? null : item.getPost();
    }

    private static void nullableProperties(OpenAPI api, String name, String... fields) {
        Schema<?> object = api.getComponents().getSchemas().get(name);
        if (object == null || object.getProperties() == null) {
            return;
        }
        for (String field : fields) {
            Schema<?> value = object.getProperties().get(field);
            if (value != null && (value.getTypes() == null || !value.getTypes().contains("null"))
                    && (value.getAnyOf() == null || value.getAnyOf().stream().noneMatch(branch ->
                    branch.getTypes() != null && branch.getTypes().contains("null")))) {
                object.addProperties(field, new io.swagger.v3.oas.models.media.ComposedSchema()
                        .addAnyOfItem(value).addAnyOfItem(nullSchema()));
            }
        }
    }

    private static Schema<?> nullSchema() {
        Schema<?> schema = new Schema<>();
        schema.setTypes(Set.of("null"));
        return schema;
    }

    private static ApiResponse errorResponse() {
        return new ApiResponse().description("Contract error. Branch on code, not message; nullable retryAfterSeconds.")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))))
                .addHeaderObject("Cache-Control", new Header().schema(new StringSchema()).description("private, no-store"))
                .addHeaderObject("X-Request-Id", new Header().schema(new StringSchema().format("uuid")))
                .addHeaderObject("WWW-Authenticate", new Header().schema(new StringSchema()).description("Present for 401 responses."))
                .addHeaderObject("Retry-After", new Header().schema(new IntegerSchema()).description("Present when retry timing applies, in seconds."));
    }
}
