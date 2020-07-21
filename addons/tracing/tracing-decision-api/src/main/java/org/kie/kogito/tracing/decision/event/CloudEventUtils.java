/*
 *  Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.kie.kogito.tracing.decision.event;

import java.net.URI;

import com.fasterxml.jackson.core.type.TypeReference;
import io.cloudevents.json.Json;
import io.cloudevents.v1.CloudEventBuilder;
import io.cloudevents.v1.CloudEventImpl;
import org.kie.kogito.tracing.decision.event.trace.TraceEvent;

public class CloudEventUtils {

    private static final TypeReference<CloudEventImpl<TraceEvent>> CLOUD_EVENT_TYPE_REF = new TypeReference<>() {
    };

    public static CloudEventImpl<TraceEvent> build(String id, URI source, TraceEvent data) {
        return CloudEventBuilder.<TraceEvent>builder()
                .withType(TraceEvent.class.getName())
                .withId(id)
                .withSource(source)
                .withData(data)
                .build();
    }

    public static String encode(CloudEventImpl<TraceEvent> event) {
        return Json.encode(event);
    }

    public static CloudEventImpl<TraceEvent> decode(String json) {
        return Json.decodeValue(json, CLOUD_EVENT_TYPE_REF);
    }

    private CloudEventUtils() {
    }
}
