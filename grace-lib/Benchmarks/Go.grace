import "harness" as harness

def size: Number = 9
def komi: Number = 7.5
def empty: Number = 1
def white: Number = 2
def black: Number = 3
def pass: Number = -1
def maxmoves: Number = size * size * 3

var globalTimestamp: Number := 0
var globalMoves: Number := 0
var random: harness.Random := done

method toPos(x': Number, y': Number) -> Number {
  var x: Number := x'
  var y: Number := y'
  y * size + x
}

method toXY(pos': Number) -> List {
  var pos: Number := pos'
  def y: Number = (pos / size).asInteger
  def x: Number = (pos % size).asInteger
  [y, x]
}

type Square = interface {
  zobristStrings
  color
  setNeighbours
  used
  move(_)
  pos
  neighbours
}

type Board = interface {
  move(_)
  reset
  useful(_)
}

type EmptySet = interface {
  randomChoice
  add(_)
  remove(_)
  set(_, _)
}

type ZobristHash = interface {
  update(_, _)
  add
  dupe
}

class newSquare(board': Board, pos': Number) -> Square {
  var board: Board := board'
  var pos: Number := pos'
  var timestamp: Number := globalTimestamp
  var removestamp: Number := globalTimestamp
  def zobristStrings: List = [random.next, random.next, random.next]
  var neighbours: List := done
  var color: Number := 0
  var reference: Square := done
  var ledges: Number := 0
  var used: Boolean := false
  var tempLedges: Number := 0

  method setNeighbours -> Done {
    def x: Number = pos % size
    def y: Number = (pos / size).asInteger

    neighbours := platform.kernel.Vector.new()
    [ [ -1, 0 ], [ 1, 0 ], [ 0, -1 ], [ 0, 1 ] ].do { d: List ->
      def dx: Number = d.at(1)
      def dy: Number = d.at(2)
      def newX: Number = x + dx
      def newY: Number = y + dy
      ((0 <= newX) && (newX < size) && (0 <= newY) && (newY < size)).ifTrue {
        neighbours.append(
          board.squares.at(toPos(newX, newY) + 1))
      }
    }
  }

  method move(color': Number) -> Done {
    globalTimestamp := globalTimestamp + 1
    globalMoves := globalMoves + 1

    board.zobrist.update(self, color')
    color := color'
    reference := self
    ledges := 0
    used := true

    neighbours.do { neighbour: Square ->
      def neighcolor: Number = neighbour.color
      // print("S.move nc: " + (neighcolor - 1) + " ledges: " + ledges)
      (neighcolor == empty).ifTrue {
        ledges := ledges + 1
      } ifFalse {
        def neighbourRef: Square = neighbour.find(true)
        // print("found ref: " + neighbourRef.pos)
        (neighcolor == color').ifTrue {
          (neighbourRef.reference.pos != pos).ifTrue {
            ledges := ledges + neighbourRef.ledges
            neighbourRef.reference := self
          }
          ledges := ledges - 1
          // print("ledges: " + ledges)
        } ifFalse {
          neighbourRef.ledges := neighbourRef.ledges - 1
          (neighbourRef.ledges == 0).ifTrue {
            // print("ledges == 0")
            neighbour.remove(neighbourRef, true)
          }
        }
      }
    }
    board.zobrist.add
  }

  method remove(reference: Square, update: Boolean) -> Done {
    board.zobrist.update(self, empty)
    removestamp := globalTimestamp
    update.ifTrue {
      color := empty
      // print("add empty " + pos)
      board.emptyset.add(pos)
    }

    neighbours.do { neighbour: Square ->
      ((neighbour.color != empty) &&
          (neighbour.removestamp != globalTimestamp)).ifTrue {
        def neighbourRef: Square = neighbour.find(update)
        (neighbourRef.pos == reference.pos).ifTrue {
          neighbour.remove(reference, update)
        } ifFalse {
          update.ifTrue {
            neighbourRef.ledges := neighbourRef.ledges + 1
          }
        }
      }
    }
  }

  method find(update: Boolean) -> Square {
    var reference': Square := reference
    (reference'.pos != pos).ifTrue {
      reference' := reference'.find(update)
      update.ifTrue {
        reference := reference'
      }
    }
    return reference'
  }
}

class newEmptySet(board': Board) -> EmptySet {
  def board: Board = board'
  def empties: List = platform.kernel.Vector.new(size * size)
  def emptyPos: List = platform.kernel.Vector.new(size * size)
  empties.appendAll(0.to((size * size) - 1))
  emptyPos.appendAll(0.to((size * size) - 1))

  method randomChoice -> Number {
    var choices: Number := empties.size
    {choices > 0}.whileTrue {
      // print("choices " + choices)
      def i: Number = (random.next % choices)
      def pos: Number = empties.at(i + 1)
      (board.useful(pos)).ifTrue {
        // print("randomChoice useful " + pos)
        return pos
      }
      // print("randomChoice not useful")
      choices := choices - 1
      set(i, empties.at(choices + 1))
      set(choices, pos)
    }
    return pass
  }

  method add(pos: Number) -> Done {
    emptyPos.at(pos + 1)put(empties.size)
    empties.append(pos)
  }

  method remove(pos: Number) -> Done {
    // print("emptyPos.size: " + emptyPos.size)
    set(emptyPos.at(pos + 1), empties.at(empties.size))
    empties.remove
  }

  method set(i: Number, pos: Number) -> Done {
    empties.at(i + 1)put(pos)
    emptyPos.at(pos + 1)put(i)
  }
}

class newZobristHash(board': Board) -> ZobristHash {
  def board: Board = board'
  def hashSet: Unknown = platform.collections.Set.new
  var hash: Number := 0
  board.squares.do { square: Square ->
    hash := hash.bitXor(square.zobristStrings.at(empty))
  }
  hashSet.removeAll
  hashSet.add(hash)

  method update(square: Square, color: Number) -> Done {
    hash := hash.bitXor(square.zobristStrings.at(square.color))
    hash := hash.bitXor(square.zobristStrings.at(color))
  }

  method add -> Done {
    hashSet.add(hash)
  }

  method dupe -> Boolean {
    return hashSet.contains(hash)
  }
}

class newBoard -> Board {
  var emptyset: EmptySet := done
  var zobrist: ZobristHash := done
  var color: Number := empty
  var finished: Boolean := false
  var lastmove: Number := -2
  var history: List := done
  var whiteDead: Number := 0
  var blackDead: Number := 0

  def squares: List = platform.kernel.Array.new(size * size)
  1.to(size * size).do { pos: Number ->
    // print(pos)
    squares.at(pos)put(newSquare(self, (pos - 1)))
  }
  squares.do { square: Square -> square.setNeighbours }
  reset()

  method reset -> Done {
    squares.do { square: Square ->
      square.color := empty
      square.used := false
    }
    emptyset := newEmptySet(self)
    zobrist := newZobristHash(self)
    color := black
    finished := false
    lastmove := -2
    history := platform.kernel.Vector.new
    whiteDead := 0
    blackDead := 0
  }

  method move(pos: Number) -> Done {
    // print("B.move: " + pos)
    (pos != pass).ifTrue {
      var square': Square := squares.at(pos + 1)
      // print("B.move square: " + square'.pos)
      square'.move(color)
      emptyset.remove(square'.pos)
    } ifFalse {
      // print("lastmove: " + lastmove)
      (lastmove == pass).ifTrue {
        // print("set finished")
        finished := true
      }
    }

    (color == black).ifTrue {
      color := white
    } ifFalse {
      color := black
    }
    lastmove := pos
    history.append(pos)
  }

  method randomMove -> Done {
    emptyset.randomChoice
  }

  method usefulFast(square: Square) -> Boolean {
    // print(square.used)
    (!square.used).ifTrue {
      // print("square.neighbours.size")
      // print(square.neighbours.size)
      square.neighbours.do { neighbour: Square ->
        // print(neighbour.color)
        (neighbour.color == empty).ifTrue {
          return true
        }
      }
    }
    return false
  }

  method useful(pos: Number) -> Boolean {
    globalTimestamp := globalTimestamp + 1
    var square: Square := squares.at(pos + 1)
    (usefulFast(square)).ifTrue {
      return true
    }

    // print("useful: not fast")
    def oldHash: Number = zobrist.hash
    zobrist.update(square, color)
    var empties: Number    := 0
    var opps: Number       := 0
    var weakOpps: Number   := 0
    var neighs: Number     := 0
    var weakNeighs: Number := 0

    square.neighbours.do { neighbour: Square ->
      def neighcolor: Number = neighbour.color
      (neighcolor == empty).ifTrue {
        empties := empties + 1
      } ifFalse {
        def neighbourRef: Square = neighbour.find(false)
        (neighbourRef.timestamp != globalTimestamp).ifTrue {
          (neighcolor == color).ifTrue {
            neighs := neighs + 1
          } ifFalse {
            opps := opps + 1
          }
          neighbourRef.timestamp := globalTimestamp
          neighbourRef.tempLedges := neighbourRef.ledges
        }
        neighbourRef.tempLedges := neighbourRef.tempLedges - 1
        (neighbourRef.tempLedges == 0).ifTrue {
          (neighcolor == color).ifTrue {
            weakNeighs := weakNeighs + 1
          } ifFalse {
            weakOpps := weakOpps + 1
            neighbourRef.remove(neighbourRef, false)
          }
        }
      }
    }
    def dupe: Boolean = zobrist.dupe()
    zobrist.hash := oldHash
    def strongNeighs: Number = neighs - weakNeighs
    def strongOpps: Number = opps - weakOpps
    // print("return: ")
    // print(!dupe && ((empties != 0) || (weakOpps != 0) || (
    //  (strongNeighs != 0) && ((strongOpps != 0) || (weakNeighs != 0)))))
    return !dupe && ((empties != 0) || (weakOpps != 0) || (
      (strongNeighs != 0) && ((strongOpps != 0) || (weakNeighs != 0))))
  }

  method usefulMoves -> List {
    // print("usefulMoves")
    // print(emptyset.empties.size)
    return emptyset.empties.select { pos: Number -> useful(pos) }
  }

  method replay(history: List) -> Done {
    // print("Replay: " + history.size)
    history.do { pos: Number -> move(pos) }
  }

  method score(color: Number) -> Number {
    var count: Number
    (color == white).ifTrue {
      count := komi + blackDead
    } ifFalse {
      count := whiteDead
    }
    squares.do { square: Square ->
      def squarecolor: Number = square.color
      (squarecolor == color).ifTrue {
        count := count + 1
      } ifFalse {
        (squarecolor == empty).ifTrue {
          var surround: Number := 0
          square.neighbours.do { neighbour: Square ->
            (neighbour.color == color).ifTrue {
              surround := surround + 1
            }
          }
          (surround == square.neighbours.size).ifTrue {
            count := count + 1
          }
        }
      }
    }
    return count
  }

  method check -> Done {
    squares.do { square: Square ->
      if (square.color != empty) then {
        def members1: Unknown = platform.collections.Set.new
        members1.add(square)

        var changed: Boolean := true
        { changed }.whileTrue {
          changed := false
          def copy: Unknown = platform.collections.Set.new
          copy.addAll(members1)
          copy.do { member: Square ->
            member.neighbours.do { neighbour: Square ->
              if ((neighbour.color == square.color) && !members1.contains(neighbour)) then {
                changed := true
                members1.add(neighbour)
              }
            }
          }
        }

        var ledges1: Number := 0
        members1.do { member: Square ->
          member.neighbours.do { neighbour: Square ->
            if (neighbour.color == empty) then {
              ledges1 := ledges1 + 1
            }
          }
        }

        def root: Square = square.find()

        // print 'members1', square, root, members1
        // print 'ledges1', square, ledges1

        def members2: Unknown = platform.collections.Set.new
        squares.do { square2: Square ->
          if ((square2.color != empty) && (square2.find() == root)) then {
            members2.add(square2)
          }
        }

        def ledges2: Number  = root.ledges
        // print 'members2', square, root, members1
        // print 'ledges2', square, ledges2

        def size1: Number = members1.size
        members1.addAll(members2)
        if (size1 != members1.size) then {
          error("members1 and members2 do not contain the same elements")
        }
        if (ledges1 != ledges2) then {
          error("ledges differ at " + square + " " + ledges1 + " " + ledges2)
        }
      }
    }
  }
}

type Node = interface {
  play(_)
  select
  playLoop(_, _, _)
}

class newUCTNode -> Node {
  var bestchild: Node := done
  var pos: Number := -1
  var wins: Number := 0
  var losses: Number := 0
  var parent: Node := done
  def posChild: List = platform.kernel.Array.new(size * size)
  var unexplored: List := done

  method playLoop(board: Board, node': Node, path: List) -> Done {
    var node: Node := node'
    true.whileTrue {
      def pos: Number = node.select()
      (pos == pass).ifTrue {
        return
      }
      board.move(pos)
      var child: Node := node.posChild.at(pos + 1)
      (child == done).ifTrue {
        child := newUCTNode()
        node.posChild.at(pos + 1)put(child)
        child.unexplored := board.usefulMoves()
        child.pos := pos
        child.parent := node
        path.append(child)
        return
      }
      path.append(child)
      node := child
    }
  }

  method play(board: Board) -> Done {
    // uct tree search
    def color: Number = board.color
    def node: Node = self
    def path: List = platform.kernel.Vector.with(node)

    playLoop(board, node, path)

    randomPlayout(board)
    updatePath(board, color, path)
  }

  method select() -> Number {
    // select move; unexplored children first, then according to uct value
    ((unexplored ~= done) && (!unexplored.isEmpty)).ifTrue {
        // print("unexplored.size " + unexplored.size)
        def i: Number = (random.next % unexplored.size) + 1
        def pos: Number = unexplored.at(i)
        unexplored.at(i)put(unexplored.at(unexplored.size))
        unexplored.remove()
        return pos
    } ifFalse {
      (bestchild ~= done).ifTrue {
        return bestchild.pos
      } ifFalse {
        return pass
      }
    }
  }

  method randomPlayout(board: Board) -> Done {
    // random play until both players pass
    // XXX while not self.finished?
    1.to(maxmoves)do { i: Number ->
      board.finished.ifTrue {
        // print("random_playout finished")
        return
      }
      board.move(board.randomMove())
    }
  }

  method updatePath(board: Board, color': Number, path: List) -> Done {
    var color: Number := color'
    // update win/loss count along path
    def wins: Boolean = board.score(black) >= board.score(white)
    path.do { node: Node ->
      (color == black).ifTrue {
          color := white
      } ifFalse {
          color := black
      }

      (wins == (color == black)).ifTrue {
        node.wins := node.wins + 1
      } ifFalse {
        node.losses := node.losses + 1
      }

      (node.parent == done).ifFalse {
        node.parent.bestchild := node.parent.bestChild()
      }
    }
  }

  method score -> Number {
    def winrate: Number = wins / (wins + losses)
    def parentvisits: Number = parent.wins + parent.losses
    (parentvisits == 0).ifTrue {
      return winrate
    }
    def nodevisits: Number = wins + losses
    return winrate + ((parentvisits + 0.0).log / (5 * nodevisits)).sqrt
  }

  method bestChild -> Node {
    var maxscore: Number := -1
    var maxchild: Node := done
    posChild.do { child: Node ->
      (child == done).ifFalse {
        (child.score() > maxscore).ifTrue {
          maxchild := child
          maxscore := child.score()
        }
      }
    }
    return maxchild
  }

  method bestVisited -> Node {
    var maxvisits: Number := -1
    var maxchild: Node := done
    posChild.do { child: Node ->
      // if child:
      //   print to_xy(child.pos), child.wins, child.losses, child.score()
      (child == done).ifFalse {
        ((child.wins + child.losses) > maxvisits).ifTrue {
          maxvisits := child.wins + child.losses
          maxchild := child
        }
      }
    }
    return maxchild
  }
}

class newGo -> harness.Benchmark {
  inherit harness.newBenchmark

  method innerBenchmarkLoop(innerIterations: Number) -> Boolean {
    def result: Number = versusCpu(innerIterations)
    return verify (innerIterations) resultFor (result)
  }

  method computerMove(board: Board, games: Number) -> Number {
    def pos: Number = board.randomMove()
    // print("randomMove " + pos)
    (pos == pass).ifTrue {
      return pass
    }
    def tree: Node = newUCTNode
    tree.unexplored := board.usefulMoves()
    def nboard: Board = newBoard

    0.to(games - 1)do { game: Number ->
      // print("new game " + game)
      def node: Node = tree
      nboard.reset()
      nboard.replay(board.history)
      node.play(nboard)
    }
    return tree.bestVisited().pos
  }

  method versusCpu(games: Number) -> Done {
    random := harness.newRandom
    def board: Board = newBoard
    return computerMove(board, games)
  }

  method verify (innerIterations: Number) resultFor (result: Number) -> Boolean {
    (innerIterations ==   10) .ifTrue { return result ==  8 }
    (innerIterations ==  100) .ifTrue { return result ==  1 }
    (innerIterations ==  200) .ifTrue { return result == 37 }
    (innerIterations ==  500) .ifTrue { return result == 79 }
    (innerIterations == 1000) .ifTrue { return result == 79 }
    print("No verification result for {innerIterations} found (results was {result}).")
    return false
  }
}

method newInstance -> harness.Benchmark { newGo }
