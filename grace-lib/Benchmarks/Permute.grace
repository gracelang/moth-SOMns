// Copyright (c) 2001-2015 see AUTHORS file
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the 'Software'), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
//
//
// Adapted for Grace by Richard Roberts
//   2018, June
//

import "harness" as harness

class newPermute -> harness.Benchmark {
  inherit harness.newBenchmark

  var count: Number := 0
  var v: List := done

  method benchmark -> Number {
    count := 0
    v := platform.kernel.Array.new(6)withAll(0)
    permute(6)
    count
  }

  method verifyResult(result: Number) -> Boolean {
    8660 == result
  }

  method permute(n: Number) -> Done {
    count := count + 1
    (n != 0).ifTrue {
      permute (n - 1)
      n.downTo(1) do { i: Number ->
        swap (n) with (i)
        permute (n - 1)
        swap (n) with (i)
      }
    }
  }

  method swap (i: Number) with (j: Number) -> Done {
    var tmp: Number := v.at(i)
    v. at (i) put (v.at(j))
    v. at (j) put (tmp)
  }
}

method newInstance -> harness.Benchmark { newPermute }
