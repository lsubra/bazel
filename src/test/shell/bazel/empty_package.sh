#!/bin/bash
#
# Copyright 2015 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Test top-level package
#

# Load test environment
source $(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/test-setup.sh \
  || { echo "test-setup.sh not found!" >&2; exit 1; }

function test_empty_package() {
  cat > BUILD <<EOF
java_binary(
    name = "noise",
    main_class = "Noise",
    srcs = ["Noise.java"],
)
EOF

  cat > Noise.java <<EOF
public class Noise {
  public static void main(String args[]) {
    System.out.println("SCREEEECH");
  }
}
EOF

  bazel run -s //:noise &> $TEST_log || fail "Failed to run //:noise"
  cat $TEST_log
  expect_log "SCREEEECH"
}

run_suite "empty package tests"