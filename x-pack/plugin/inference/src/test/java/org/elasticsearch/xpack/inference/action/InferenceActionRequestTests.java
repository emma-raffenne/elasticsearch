/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.action;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.TransportVersions;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.inference.InputType;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.elasticsearch.xpack.core.inference.action.InferenceAction;
import org.elasticsearch.xpack.core.ml.AbstractBWCWireSerializationTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

public class InferenceActionRequestTests extends AbstractBWCWireSerializationTestCase<InferenceAction.Request> {

    @Override
    protected Writeable.Reader<InferenceAction.Request> instanceReader() {
        return InferenceAction.Request::new;
    }

    @Override
    protected InferenceAction.Request createTestInstance() {
        return new InferenceAction.Request(
            randomFrom(TaskType.values()),
            randomAlphaOfLength(6),
            randomList(1, 5, () -> randomAlphaOfLength(8)),
            randomMap(0, 3, () -> new Tuple<>(randomAlphaOfLength(4), randomAlphaOfLength(4))),
            randomFrom(InputType.values())
        );
    }

    public void testParsing() throws IOException {
        String singleInputRequest = """
            {
              "input": "single text input"
            }
            """;
        try (var parser = createParser(JsonXContent.jsonXContent, singleInputRequest)) {
            var request = InferenceAction.Request.parseRequest("model_id", "sparse_embedding", parser);
            assertThat(request.getInput(), contains("single text input"));
        }

        String multiInputRequest = """
            {
              "input": ["an array", "of", "inputs"]
            }
            """;
        try (var parser = createParser(JsonXContent.jsonXContent, multiInputRequest)) {
            var request = InferenceAction.Request.parseRequest("model_id", "sparse_embedding", parser);
            assertThat(request.getInput(), contains("an array", "of", "inputs"));
        }
    }

    public void testParseRequest_DefaultsInputTypeToIngest() throws IOException {
        String singleInputRequest = """
            {
              "input": "single text input"
            }
            """;
        try (var parser = createParser(JsonXContent.jsonXContent, singleInputRequest)) {
            var request = InferenceAction.Request.parseRequest("model_id", "sparse_embedding", parser);
            assertThat(request.getInputType(), is(InputType.UNSPECIFIED));
        }
    }

    @Override
    protected InferenceAction.Request mutateInstance(InferenceAction.Request instance) throws IOException {
        int select = randomIntBetween(0, 4);
        return switch (select) {
            case 0 -> {
                var nextTask = TaskType.values()[(instance.getTaskType().ordinal() + 1) % TaskType.values().length];
                yield new InferenceAction.Request(
                    nextTask,
                    instance.getInferenceEntityId(),
                    instance.getInput(),
                    instance.getTaskSettings(),
                    instance.getInputType()
                );
            }
            case 1 -> new InferenceAction.Request(
                instance.getTaskType(),
                instance.getInferenceEntityId() + "foo",
                instance.getInput(),
                instance.getTaskSettings(),
                instance.getInputType()
            );
            case 2 -> {
                var changedInputs = new ArrayList<String>(instance.getInput());
                changedInputs.add("bar");
                yield new InferenceAction.Request(
                    instance.getTaskType(),
                    instance.getInferenceEntityId(),
                    changedInputs,
                    instance.getTaskSettings(),
                    instance.getInputType()
                );
            }
            case 3 -> {
                var taskSettings = new HashMap<>(instance.getTaskSettings());
                if (taskSettings.isEmpty()) {
                    taskSettings.put("foo", "bar");
                } else {
                    var keyToRemove = taskSettings.keySet().iterator().next();
                    taskSettings.remove(keyToRemove);
                }
                yield new InferenceAction.Request(
                    instance.getTaskType(),
                    instance.getInferenceEntityId(),
                    instance.getInput(),
                    taskSettings,
                    instance.getInputType()
                );
            }
            case 4 -> {
                var nextInputType = InputType.values()[(instance.getInputType().ordinal() + 1) % InputType.values().length];
                yield new InferenceAction.Request(
                    instance.getTaskType(),
                    instance.getInferenceEntityId(),
                    instance.getInput(),
                    instance.getTaskSettings(),
                    nextInputType
                );
            }
            default -> throw new UnsupportedOperationException();
        };
    }

    @Override
    protected InferenceAction.Request mutateInstanceForVersion(InferenceAction.Request instance, TransportVersion version) {
        if (version.before(TransportVersions.INFERENCE_MULTIPLE_INPUTS)) {
            return new InferenceAction.Request(
                instance.getTaskType(),
                instance.getInferenceEntityId(),
                instance.getInput().subList(0, 1),
                instance.getTaskSettings(),
                InputType.UNSPECIFIED
            );
        } else if (version.before(TransportVersions.ML_INFERENCE_REQUEST_INPUT_TYPE_ADDED)) {
            return new InferenceAction.Request(
                instance.getTaskType(),
                instance.getInferenceEntityId(),
                instance.getInput(),
                instance.getTaskSettings(),
                InputType.UNSPECIFIED
            );
        } else if (version.before(TransportVersions.ML_INFERENCE_REQUEST_INPUT_TYPE_UNSPECIFIED_ADDED)
            && instance.getInputType() == InputType.UNSPECIFIED) {
                return new InferenceAction.Request(
                    instance.getTaskType(),
                    instance.getInferenceEntityId(),
                    instance.getInput(),
                    instance.getTaskSettings(),
                    InputType.INGEST
                );
            }

        return instance;
    }

    public void testWriteTo_WhenVersionIsOnAfterUnspecifiedAdded() throws IOException {
        assertBwcSerialization(
            new InferenceAction.Request(TaskType.TEXT_EMBEDDING, "model", List.of(), Map.of(), InputType.UNSPECIFIED),
            TransportVersions.ML_INFERENCE_REQUEST_INPUT_TYPE_UNSPECIFIED_ADDED
        );
    }

    public void testWriteTo_WhenVersionIsBeforeUnspecifiedAdded_ButAfterInputTypeAdded_ShouldSetToIngest() throws IOException {
        assertBwcSerialization(
            new InferenceAction.Request(TaskType.TEXT_EMBEDDING, "model", List.of(), Map.of(), InputType.UNSPECIFIED),
            TransportVersions.ML_INFERENCE_REQUEST_INPUT_TYPE_ADDED
        );
    }

    public void testWriteTo_WhenVersionIsBeforeUnspecifiedAdded_ButAfterInputTypeAdded_ShouldSetToIngest_ManualCheck() throws IOException {
        var instance = new InferenceAction.Request(TaskType.TEXT_EMBEDDING, "model", List.of(), Map.of(), InputType.UNSPECIFIED);

        InferenceAction.Request deserializedInstance = copyWriteable(
            instance,
            getNamedWriteableRegistry(),
            instanceReader(),
            TransportVersions.ML_INFERENCE_REQUEST_INPUT_TYPE_ADDED
        );

        assertThat(deserializedInstance.getInputType(), is(InputType.INGEST));
    }

    public void testWriteTo_WhenVersionIsBeforeInputTypeAdded_ShouldSetInputTypeToUnspecified() throws IOException {
        var instance = new InferenceAction.Request(TaskType.TEXT_EMBEDDING, "model", List.of(), Map.of(), InputType.INGEST);

        InferenceAction.Request deserializedInstance = copyWriteable(
            instance,
            getNamedWriteableRegistry(),
            instanceReader(),
            TransportVersions.HOT_THREADS_AS_BYTES
        );

        assertThat(deserializedInstance.getInputType(), is(InputType.UNSPECIFIED));
    }
}
