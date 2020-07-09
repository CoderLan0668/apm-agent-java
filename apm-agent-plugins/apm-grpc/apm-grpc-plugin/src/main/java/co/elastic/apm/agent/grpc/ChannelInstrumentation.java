/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.grpc.helper.GrpcHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link Channel#newCall(MethodDescriptor, CallOptions)} to add channel authority (host+port) to the span
 * linked to the returned client call instance (if any).
 */
public class ChannelInstrumentation extends BaseInstrumentation {

    public ChannelInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("io.grpc")
            .and(nameContains("Channel"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("io.grpc.Channel"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("newCall");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void onEnter(@Advice.This Channel channel,
                                @Advice.Argument(0) MethodDescriptor<?, ?> method,
                                @Advice.Local("span") Span span) {

        if (grpcHelperManager == null) {
            return;
        }

        GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.class);
        if (helper != null) {
            span = helper.startSpan(tracer.getActive(), method, channel.authority());
        }

    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void onExit(@Advice.Return @Nullable ClientCall<?, ?> clientCall,
                               @Advice.Local("span") @Nullable Span span) {

        if (grpcHelperManager == null || span == null) {
            return;
        }

        GrpcHelper helper = grpcHelperManager.getForClassLoaderOfClass(ClientCall.class);
        if (helper != null) {
            helper.registerSpan(clientCall, span);
        }

    }

}
