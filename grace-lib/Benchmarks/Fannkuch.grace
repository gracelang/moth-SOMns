// Copyright (c) 2001-2018 see AUTHORS file
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

class newFannkuchBenchmark -> harness.Benchmark {
  inherit harness.newBenchmark

  var perm: List
  var timesRotated: List
  var atEnd: Boolean := false

  method innerBenchmarkLoop(innerIterations: Number) -> Boolean {
    perm := 1.to(innerIterations)
    timesRotated := platform.kernel.Array.new(innerIterations)withAll(0)
    atEnd := false

    return verify(maxPfannkuchen)inner(innerIterations)
  }

  method verify(result: Number)inner(innerIterations: Number) -> Boolean {
    (innerIterations == 10).ifTrue { return result == 38 }
    (innerIterations == 9).ifTrue { return result == 30 }
    (innerIterations == 8).ifTrue { return result == 22 }
    (innerIterations == 7).ifTrue { return result == 16 }
    (innerIterations == 2).ifTrue { return result == 1 }
    (innerIterations == 1).ifTrue { return result == 0 }

    print("No verification result for {innerIterations} found")
    print("Result is: {result}")
    return false
  }

  method pfannkuchen (anArray: List) -> Number {
    var k: Number := 0
    var complement: Number
    var first: Number := anArray.at(1)

    { first == 1 }.whileFalse {

      k := k + 1
      complement := first + 1

      1.to((first / 2)) do { i: Number ->
        var a: Number := anArray.at(i)
        var b: Number := anArray.at(complement - i)
        anArray.at (i) put (b)
        anArray.at (complement - i) put (a)

        first := anArray.at(1)
      }
    }

    k
  }

  method makeNext -> Done {

    // Generate the next permutation.
    2.to (perm.size) do { r: Number ->

      // Rotate the first r items to the left.
      var temp: Number := perm.at (1)
      1.to(r - 1) do { i: Number ->
        perm.at(i) put (perm.at(i + 1))
      }
      perm.at (r) put (temp)

      timesRotated.at (r) put ((timesRotated.at(r) + 1) % r)
      var remainder: Number := timesRotated.at (r)
      (remainder == 0).ifFalse {
        return self
      }

      // After r rotations, the first r items are in their original positions.
      //   Go on rotating the first r+1 items.
    }

    // We are past the final permutation.
    atEnd := true
    Done
  }

  method maxPfannkuchen -> Number {
    var max: Number := 0
    { atEnd }.whileFalse {
      max := max.max (pfannkuchen (next))
    }
    max
  }

  method next -> List {
    var result: List := perm.copy
    makeNext
    result
  }
}

method newInstance -> harness.Benchmark { newFannkuchBenchmark }
