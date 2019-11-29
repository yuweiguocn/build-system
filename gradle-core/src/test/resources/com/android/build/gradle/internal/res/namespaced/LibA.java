/*
 * Copyright (C) 2018 The Android Open Source Project
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

package lib.A;

public class LibA {
    public static int test() {
        // When rewritting the following substitution should take place:
        // lib.A.R.styleable.s2_attr1 -> lib.A.R.styleable.s2_lib_C_attr1
        // lib.A.R.styleable.s2_attr2 -> lib.A.R.styleable.s2_lib_B_attr2
        // lib.A.R.styleable.s2_attr3 -> lib.A.R.styleable.s2_lib_B_attr3
        // lib.A.R.styleable.s2_attr4 -> lib.A.R.styleable.s2_attr4 //unchanged
        // lib.A.R.styleable.s1_attr1 -> lib.B.R.styleable.s1_lib_C_attr1
        // lib.A.R.styleable.s1_attr2 -> lib.B.R.styleable.s1_attr2 //parent and child package match

        int ref1 = R.styleable.s1_attr1;
        int ref2 = R.styleable.s1_attr2;
        int ref3 = R.styleable.s2_attr1;
        int ref4 = R.styleable.s2_attr2;
        int ref5 = R.styleable.s2_attr3;
        int ref6 = R.styleable.s2_attr4;
        return ref1 + ref2 + ref3 + ref4 + ref5 + ref6;
    }
}
