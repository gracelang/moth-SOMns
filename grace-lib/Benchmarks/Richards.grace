import "harness" as harness

def NoTask: Done = done
def NoWork: Done = done

def            Idler: Number = 1
def           Worker: Number = 2
def   WorkPacketKind: Number = 2
def         HandlerA: Number = 3
def         HandlerB: Number = 4
def          DeviceA: Number = 5
def          DeviceB: Number = 6
def DevicePacketKind: Number = 1

def tracing: Boolean = false

type RBObject = interface {
  append (_) head (_)
}

type Packet = interface {
  link
  identity
  kind
  datum
  data
  asString
}

type Scheduler = interface {
  start
}

type TaskControlBlock = interface {
  packetPending(_)
  taskWaiting(_)
  taskHolding(_)
}

type DeviceTaskDataRecord = interface {
  pending
}

type TaskState = interface {
  append (packet) head (queueHead)
  packetPending'
  taskWaiting'
  taskHolding'
  isPacketPending
  isTaskHolding
  isTaskWaiting
  taskHolding (aBoolean)
  taskWaiting (aBoolean)
  packetPending (aBoolean)
  packetPending
  running
  waiting
  waitingWithPacket
  isRunning
  isTaskHoldingOrWaiting
  isWaiting
  isWaitingWithPacket
}

type HandlerTaskDataRecord = interface {
  workIn
  deviceIn
  deviceInAdd(packet)
  workInAdd(packet)
  asString
}

type IdleTaskDataRecord = interface {
  control
  count
}

type WorkerTaskDataRecord = interface {
  destination
  count
}

class newRichards -> harness.Benchmark {
  inherit harness.newBenchmark

  method benchmark() -> Boolean {
    newScheduler.start
  }

  method verifyResult(result: Boolean) -> Boolean {
    result
  }
}

class newRBObject -> RBObject {
  method append (packet: Packet) head (queueHead: Packet) -> Packet {
    packet.link(NoWork)
    (NoWork == queueHead).ifTrue { return packet }

    var mouse: Packet := queueHead
    var link: Packet
    {
      link := mouse.link
      NoWork == link
    }. whileFalse { mouse := link }

    mouse.link(packet)
    return queueHead
  }
}

class newScheduler -> Scheduler {
  var taskList: TaskControlBlock    := NoTask
  var currentTask: TaskControlBlock := NoTask
  var currentTaskIdentity: Number   := 0
  var taskTable: List               := platform.kernel.Array.new(6) withAll (NoTask)
  var layout: Number                := 0
  var queuePacketCount: Number      := 0
  var holdCount: Number             := 0

  method createDevice (identity: Number) priority (priority: Number)
                 work (work: Packet) state (state: TaskState) -> Done {
      var data: DeviceTaskDataRecord := newDeviceTaskDataRecord

      createTask (identity) priority (priority) work (work) state (state)
             function { work: Packet, word: RBObject ->
          var data: DeviceTaskDataRecord := word
          var functionWork: Packet := work

          (NoWork == functionWork). ifTrue {
            functionWork := data.pending

            (NoWork == functionWork). ifTrue {
              wait
            } ifFalse {
              data.pending := NoWork
              queuePacket (functionWork)
            }
          } ifFalse {
              data.pending := functionWork
              tracing.ifTrue {
                trace (functionWork.datum)
              }
              holdSelf
            }
          }
          data (data)
  }

  method createHandler (identity: Number) priority (priority: Number)
                  work (work: Packet) state (state: TaskState) -> Done {
    var data: HandlerTaskDataRecord := newHandlerTaskDataRecord

    createTask (identity) priority (priority) work (work) state (state)
         function { work: Packet, word: RBObject ->
            var data: HandlerTaskDataRecord := word

            (NoWork == work).ifFalse {
              (WorkPacketKind == work.kind).ifTrue {
                data.workInAdd(work)
              } ifFalse {
                data.deviceInAdd(work)
              }
            }

            var workPacket: Packet := data.workIn
            (NoWork == workPacket).ifTrue {
              wait
            } ifFalse {
              var count: Number := workPacket.datum
              (count > 4).ifTrue {
                data.workIn(workPacket.link)
                queuePacket(workPacket)
              } ifFalse {
                var devicePacket: Packet := data.deviceIn
                (NoWork == devicePacket).ifTrue {
                  wait
                } ifFalse {
                  data.deviceIn(devicePacket.link)
                  devicePacket.datum(workPacket.data.at(count))
                  workPacket.datum(count + 1)
                  queuePacket(devicePacket)
                }
              }
            }
          }
          data(data)
  }

  method createIdler (identity: Number) priority (priority: Number)
                work (work: Packet) state (state: TaskState) -> Done {
    var data: IdleTaskDataRecord := newIdleTaskDataRecord
    createTask(identity) priority(priority) work(work) state(state)
         function { work: Packet, word: RBObject ->
           var data: RBObject := word
           data.count(data.count - 1)
           (0 == data.count).ifTrue {
            holdSelf
           } ifFalse {
             (0 == (data.control & 1)).ifTrue {
               data.control((data.control / 2))
               release(DeviceA)
             } ifFalse {
               data.control(((data.control / 2)).bitXor(53256))
               release(DeviceB)
             }
           }
         }
         data(data)
  }

  method createPacket (link: Packet) identity (identity: Number) kind (kind: Number) -> Packet {
    Packet (link) identity (identity) kind (kind)
  }

  method createTask (identity: Number) priority (priority: Number)
               work (work: Packet) state (state: TaskState)
           function (aBlock: Invokable) data (data: RBObject) -> Done {
    var t: TaskControlBlock := newTaskControlBlock (taskList) create(identity)
                                           priority(priority) initialWorkQueue(work)
                                       initialState(state) function(aBlock)
                                        privateData(data)
    taskList := t
    taskTable.at(identity) put(t)
  }

  method createWorker (identity: Number) priority (priority: Number)
                 work (work: Packet) state (state: TaskState) -> Done {
    var data: WorkerTaskDataRecord := newWorkerTaskDataRecord
    createTask (identity) priority(priority) work(work) state(state)
          function { work: Packet, word: RBObject ->
            var data: WorkerTaskDataRecord := word

            (NoWork == work).ifTrue {
              wait
            } ifFalse {
              data.destination := (HandlerA == data.destination).ifTrue { HandlerB } ifFalse { HandlerA }

              work.identity(data.destination)
              work.datum(1)
              1.to(4) do { i: Number ->
               data.count (data.count + 1)
               (data.count > 26).ifTrue { data.count(1) }
               work.data.at(i)put(65 + data.count - 1)
              }

              queuePacket(work)
            }
          }
          data (data)
  }

  method start -> Boolean {
    var workQ: Packet

    createIdler(Idler) priority(0) work(NoWork) state(newTaskStateRunning)
    workQ := createPacket(NoWork) identity(Worker) kind(WorkPacketKind)
    workQ := createPacket(workQ) identity(Worker) kind(WorkPacketKind)
    createWorker(Worker) priority(1000) work(workQ) state(newTaskStateWaitingWithPacket)

    workQ := createPacket(NoWork) identity(DeviceA) kind(DevicePacketKind)
    workQ := createPacket(workQ) identity(DeviceA) kind(DevicePacketKind)
    workQ := createPacket(workQ) identity(DeviceA) kind(DevicePacketKind)

    createHandler(HandlerA) priority(2000) work(workQ) state(newTaskStateWaitingWithPacket)
    workQ := createPacket(NoWork) identity(DeviceB) kind(DevicePacketKind)
    workQ := createPacket(workQ) identity(DeviceB) kind(DevicePacketKind)
    workQ := createPacket(workQ) identity(DeviceB) kind(DevicePacketKind)

    createHandler(HandlerB) priority(3000) work(workQ) state (newTaskStateWaitingWithPacket)
    createDevice(DeviceA) priority(4000) work(NoWork) state (newTaskStateWaiting)
    createDevice(DeviceB) priority(5000) work(NoWork) state (newTaskStateWaiting)

    schedule

    (queuePacketCount == 23246) && (holdCount == 9297)
  }

  method findTask (identity: Number) -> TaskControlBlock {
    var t: TaskControlBlock := taskTable.at(identity)
    (NoTask == t).ifTrue {error("findTask failed")}
    t
  }

  method holdSelf -> TaskControlBlock {
    holdCount := holdCount + 1
    currentTask.taskHolding(true)
    currentTask.link
  }

  method queuePacket (packet: Packet) -> Done {
    var t: TaskControlBlock := findTask(packet.identity)
    (NoTask == t).ifTrue { return NoTask }

    queuePacketCount := queuePacketCount + 1
    packet.link(NoWork)
    packet.identity(currentTaskIdentity)
    return t.addInput(packet) checkPriority(currentTask)
  }

  method release (identity: Number) -> Done {
    var t: TaskControlBlock := findTask (identity)
    (NoTask == t). ifTrue { return NoTask }
    t.taskHolding (false)
    (t.priority > currentTask.priority).ifTrue  { return t } ifFalse { return currentTask }
  }

  method trace (id: Number) -> Done {
    layout := layout - 1
    (0 >= layout).ifTrue {
      print("")
      layout := 50
    }
    print(id.asString)
  }

  method wait -> TaskControlBlock {
    currentTask.taskWaiting(true)
    currentTask
  }

  method schedule -> Done {
    currentTask := taskList

    { NoTask == currentTask }.whileFalse {

      currentTask.isTaskHoldingOrWaiting.ifTrue {
        currentTask := currentTask.link

      } ifFalse {
        currentTaskIdentity := currentTask.identity
        tracing.ifTrue { trace(currentTaskIdentity) }
        currentTask := currentTask.runTask
      }
    }
  }
}

class Packet (link': Packet) identity (identity': Number) kind (kind': Number) -> Packet {
  var     link: Packet := link'
  var identity: Number := identity'
  var     kind: Number := kind'
  var    datum: Number := 1
  var     data: List := platform.kernel.Array.new(4)withAll(0)

  method asString -> String {
    "Packet({link.asString}, {identity.asString}, {kind.asString}, {datum.asString}, {data.asString})"
  }
}

class newDeviceTaskDataRecord -> DeviceTaskDataRecord {
  inherit newRBObject
  var pending: Packet := NoWork
}

class newHandlerTaskDataRecord -> HandlerTaskDataRecord {
  inherit newRBObject
  var workIn: Packet := NoWork
  var deviceIn: Packet := NoWork

  method deviceInAdd (packet: Packet) -> Packet {
    deviceIn := append (packet) head (deviceIn)
  }

  method workInAdd (packet: Packet) -> Packet {
    workIn := append (packet) head (workIn)
  }

  method asString -> String {
    "HandlerTaskDataRecord({workIn.asString}, {deviceIn.asString})"
  }
}

class newIdleTaskDataRecord -> IdleTaskDataRecord {
  inherit newRBObject

  var control: Number := 1
  var count: Number := 10000
}

class newTaskState -> TaskState {
  inherit newRBObject

  var packetPending': Boolean
  var taskWaiting': Boolean
  var taskHolding': Boolean

  method isPacketPending -> Boolean { packetPending' }
  method isTaskHolding   -> Boolean { taskHolding' }
  method isTaskWaiting   -> Boolean { taskWaiting' }

  method taskHolding (aBoolean: Boolean) -> Done { taskHolding' := aBoolean }
  method taskWaiting (aBoolean: Boolean) -> Done { taskWaiting' := aBoolean }
  method packetPending (aBoolean: Boolean) -> Done { packetPending' := aBoolean }

  method packetPending -> Done {
    packetPending' := true
    taskWaiting' := false
    taskHolding' := false
    done
  }

  method running -> Done {
    packetPending' := false
    taskWaiting' := false
    taskHolding' := false
    done
  }

  method waiting -> Done {
    packetPending' := false
    taskHolding' := false
    taskWaiting' :=  true
    done
  }

  method waitingWithPacket -> Done {
    taskHolding' := false
    taskWaiting' := true
    packetPending' := true
    done
  }

  method isRunning -> Boolean {
    !packetPending' && !taskWaiting' && !taskHolding'
  }

  method isTaskHoldingOrWaiting -> Boolean {
    taskHolding' || (!packetPending' && taskWaiting')
  }

  method isWaiting -> Boolean {
    !packetPending' && taskWaiting' && !taskHolding'
  }

  method isWaitingWithPacket -> Boolean {
    packetPending' && taskWaiting' && !taskHolding'
  }
}

method newTaskStatePacketPending -> TaskState {
  var ret: TaskState := newTaskState
  ret.packetPending
  ret
}

method newTaskStateRunning -> TaskState {
  var ret: TaskState := newTaskState
  ret.running
  ret
}

method newTaskStateWaiting -> TaskState {
  var ret: TaskState := newTaskState
  ret.waiting
  ret
}

method newTaskStateWaitingWithPacket -> TaskState {
  var ret: TaskState := newTaskState
  ret.waitingWithPacket
  ret
}

class newTaskControlBlock (link': TaskControlBlock) create (identity': Number) priority (priority': Number) initialWorkQueue (initialWorkQueue': Packet) initialState (initialState': TaskState) function (aBlock': Invokable) privateData (privateData': RBObject) -> TaskControlBlock {
  inherit newTaskState

  def link: TaskControlBlock = link'
  def identity: Number = identity'
  def priority: Number = priority'
  def initialWorkQueue: Packet = initialWorkQueue'
  def initialState: TaskState = initialState'
  def aBlock: Invokable = aBlock'
  def privateData: RBObject = privateData'

  var input: Packet := initialWorkQueue
  var handle: RBObject := privateData
  var function: Invokable := aBlock
  packetPending(initialState.isPacketPending)
  taskWaiting(initialState.isTaskWaiting)
  taskHolding(initialState.isTaskHolding)

  method addInput (packet: Packet) checkPriority (oldTask: TaskControlBlock) -> TaskControlBlock {
    (NoWork == input).ifTrue {
      input := packet
      packetPending(true)
      (priority > oldTask.priority).ifTrue { return self }
    } ifFalse {
      input := append (packet) head (input)
    }

    oldTask
  }

  method runTask -> TaskControlBlock {
    var message: Packet

    isWaitingWithPacket.ifTrue {
      message := input
      input := message.link
      (NoWork == input).ifTrue {
        running
      } ifFalse {
        packetPending
      }
    } ifFalse {
      message := NoWork
    }

    function.apply (message, handle)
  }
}

class newWorkerTaskDataRecord -> WorkerTaskDataRecord {
  inherit newRBObject

  var destination: Number := HandlerA
  var count: Number := 0
}

method newInstance -> harness.Benchmark { newRichards }
