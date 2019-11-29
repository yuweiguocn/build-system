/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.java8;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;

public class ExtendClassWithLambda extends BaseClassWithLambda {
    @Override
    public List<Integer> foo() {
        ImmutableList<Integer> inputs =
                ImmutableList.<Integer>builder().add(7).addAll(super.foo()).build();
        return inputs.stream().map(value -> value * 5).collect(Collectors.toList());
    }

    public List<Integer> bar() {
        ImmutableList<Integer> inputs = ImmutableList.of(10, 20, 30);
        return inputs.stream().map(value -> value * 7).collect(Collectors.toList());
    }
}
