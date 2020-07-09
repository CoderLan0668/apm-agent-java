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
package co.elastic.apm.agent.jdbc;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class DataSourceInstrumentation extends JdbcInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("javax.sql.DataSource")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getConnection");
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Nullable @Advice.Local("span") Object span) {
        if (tracer.getActive() == null) {
            return;
        }

        AbstractSpan<?> parent = tracer.getActive();
        span = parent.createSpan()
            .withName("DataSource#getConnection")
            .activate();
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Nullable @Advice.Local("span") Object span) {
        if (span != null) {
            ((Span) span).deactivate().end();
        }
    }
}
