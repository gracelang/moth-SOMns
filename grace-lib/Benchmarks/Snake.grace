import "harness" as harness


def BOARDHEIGHT: Number = 20
def BOARDWIDTH: Number  = 30

type Position = interface {
  x
  y
}

class newPosition(x': Number, y': Number) -> Position {
  def x: Number = x'
  def y: Number = y'
  method asString -> String {
    return "({x}, {y})"
  }
}

method samePosition(a': Position, b': Position) -> Boolean {
  def a: Position = a'
  def b: Position = b'
  (a.x == b.x) && (a.y == b.y)
}

type Snake = {
  segments
  direction
  head
  collidedWithWall
  collidedWithSelf
  nextHead
  slither
  grow
  asString
}

class newSnake(segments': List) -> Snake {
  def segments: List = segments'
  var direction: String

  method head -> Position {
    return segments.at(segments.size)
  }

  method collidedWithWall -> Boolean {
    (head.x <= 0) || (
      head.x >= BOARDWIDTH)  || (
        head.y <= 0) || (
          head.y >= BOARDHEIGHT)
  }

  method collidedWithSelf -> Boolean {
    1.to(segments.size) do { i: Number ->
      (i + 1).to(segments.size) do { j: Number ->
        (samePosition(segments.at(i), segments.at(j))).ifTrue {
          return true
        }
      }
    }
    return false
  }

  method nextHead -> Position {
    ("right" == direction). ifTrue { return newPosition(head.x + 1, head.y              ) }
    ("left"  == direction). ifTrue { return newPosition(head.x - 1, head.y              ) }
    ("down"  == direction). ifTrue { return newPosition(head.x,               head.y - 1) }
    ("up"    == direction). ifTrue { return newPosition(head.x,               head.y + 1) }
    error("{direction} not understood as a direction?")
  }

  method slither -> Done {
    segments.append(nextHead)
    segments.remove(segments.at(1))
    done
  }

  method grow -> Done {
    segments.append(nextHead)
    done
  }

  method isTouching(position: Position) -> Boolean {
    segments.do { seg: Position ->
      samePosition(seg, position).ifTrue { return true }
    }
    return false
  }

  method asString -> String {
    var s: String := "Snake\n  segs={segments.size}\n"
    segments.do { seg: Position ->
      s := "{s}  {seg}\n"
    }
    return s
  }
}

type World = interface {
  food
  snake
  isGameOver
  tick
}

class newWorld -> World {
  var snake: Snake
  var food: Position
  var moves: Number := 0
  var random: harness.Random

  method reset -> Done {
    random := harness.newRandom
    random.seed := 1324

    var segments: List := platform.kernel.Vector.new
    segments.append(newPosition(10, 15))
    snake := newSnake(segments)
    snake.direction := "right"
    food := randomPosition
    moves := 0
  }

  method isGameOver -> Boolean {
    snake.collidedWithWall || snake.collidedWithSelf
  }

  method randomDouble -> Number {
    (random.next + 0.0) / 65535.0
  }

  method randomBetween(x: Number)and(y: Number) -> Number {
    (x + ((y + 1) - x) * randomDouble).asInteger
  }

  method randomPosition -> Position {
     newPosition ( randomBetween(1)and(BOARDWIDTH - 1),
                   randomBetween(1)and(BOARDHEIGHT - 1) )
  }

  method tick -> Done {
    samePosition(food, snake.head).ifTrue {
      snake.grow
      food := randomPosition
    } ifFalse {
      snake.slither
    }

    moves := moves + 1
  }

  method handleKey (key: String) -> Done {
    (key == "w"). ifTrue {
      snake.direction := "up"
      return done
    }
    (key == "s"). ifTrue {
      snake.direction := "down"
      return done
    }
    (key == "a"). ifTrue {
      snake.direction := "left"
      return done
    }
    (key == "d"). ifTrue {
      snake.direction := "right"
      return done
    }

    error("{key} not understood as a key?")
  }

  method render -> Done {
    var renderStr: String := ""

    0.asInteger.to(BOARDHEIGHT) do { y: Number ->
      var rowStr: String := ""

      0.asInteger.to(BOARDWIDTH) do { x: Number ->
        var p: Position := newPosition(x, y)

        var isWall: Boolean  := (x <= 0) || (x >= BOARDWIDTH) || (y <= 0) || (y >= BOARDHEIGHT)
        var isSnake: Boolean := snake.isTouching(p)
        var isFood: Boolean  := samePosition(food, p)

        isSnake.ifTrue { rowStr := rowStr ++ "S" } ifFalse {
          isFood.ifTrue { rowStr := rowStr ++ "O" } ifFalse {
            isWall.ifTrue { rowStr := rowStr ++ "X" } ifFalse {
              rowStr := rowStr ++ " "
            }
          }
        }
      }

      renderStr := "{rowStr}\n" + renderStr
    }

    print(renderStr)
  }

}

method replay (world': World, history': List) -> Done {
  def world: World = world'
  def history: List = history'
  world.reset

  history.do { item: String ->

    (item == "t").ifTrue {
      world.tick
      // world.render
      world.isGameOver.ifTrue {
        return done
      }
    } ifFalse {
      world.handleKey(item)
    }
  }

  done
}

class newSnakeBenchmark -> harness.Benchmark {
  inherit harness.newBenchmark

  def world: World = newWorld
  def history: List = [
    "t", "t", "t", "t", "t", "t", "t", "t", "t", "t",
    "s", "t", "t", "t", "d", "t", "t", "t",
    "w", "t", "t", "t", "t", "t", "t",
    "a", "t", "t", "t", "t", "t", "t", "t",
    "s", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t",
    "a", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t",
    "w", "t", "t", "t", "t",
    "d", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t",
    "w", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t",
    "a", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t",
    "s", "t", "t", "t",
    "d", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t",
    "w", "t", "t", "t", "t", "t", "t",
    "a", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t",
    "s", "t", "t", "t", "t", "t", "t", "t", "t", "t", "t",
    "a", "t", "t",
    "w", "t", "t",
    "d", "t", "t", "t", "t", "t", "t"
  ]

  method benchmark -> World {
    replay(world, history)
    world
  }

  method verifyResult(world: World) -> Boolean {
    (world.moves == 157) &&
      (world.snake.segments.size == 10) &&
      (world.isGameOver)
  }
}

method newInstance -> harness.Benchmark { newSnakeBenchmark }
