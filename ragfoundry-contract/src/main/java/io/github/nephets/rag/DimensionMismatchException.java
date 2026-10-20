/*
 * Copyright 2026 The ragfoundry Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.nephets.rag;

/** 两个向量维度不一致时抛出。继承 {@link IllegalArgumentException} 以便调用方按非法参数统一处理。 */
public class DimensionMismatchException extends IllegalArgumentException {

    private final int expected;
    private final int actual;

    public DimensionMismatchException(String message, int expected, int actual) {
        super(message + "（期望维度=" + expected + "，实际维度=" + actual + "）");
        this.expected = expected;
        this.actual = actual;
    }

    public int expected() {
        return expected;
    }

    public int actual() {
        return actual;
    }
}
