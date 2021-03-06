/*
 * Copyright (c) 2017-2019 The Typelevel Cats-effect Project Developers
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

package cats.effect
package concurrent

import cats.data.State
import cats.effect.IO

import scala.concurrent.Future

class LensRefTests extends CatsEffectSuite {

  private def run(t: IO[Unit]): Future[Unit] = t.as(assert(true)).unsafeToFuture()

  case class Foo(bar: Integer, baz: Integer)

  object Foo {
    def get(foo: Foo): Integer = foo.bar

    def set(foo: Foo)(bar: Integer): Foo = foo.copy(bar = bar)
  }

  test("get - returns B") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      result <- refB.get
    } yield result

    run(op.map(n => assertEquals(n.toInt, 0)))
  }

  test("set - modifies underlying Ref") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      _ <- refB.set(1)
      result <- refA.get
    } yield result

    run(op.map(assertEquals(_, Foo(1, -1))))
  }

  test("getAndSet - modifies underlying Ref and returns previous value") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      oldValue <- refB.getAndSet(1)
      a <- refA.get
    } yield (oldValue, a)

    run(op.map {
      case (oldValue, a) =>
        assertEquals(oldValue.toInt, 0)
        assertEquals(a, Foo(1, -1))
    })
  }

  test("update - modifies underlying Ref") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      _ <- refB.update(_ + 1)
      a <- refA.get
    } yield a

    run(op.map(assertEquals(_, Foo(1, -1))))
  }

  test("modify - modifies underlying Ref and returns a value") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      result <- refB.modify(bar => (bar + 1, 10))
      a <- refA.get
    } yield (result, a)

    run(op.map {
      case (result, a) =>
        assertEquals(result, 10)
        assertEquals(a, Foo(1, -1))
    })
  }

  test("tryUpdate - successfully modifies underlying Ref") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      result <- refB.tryUpdate(_ + 1)
      a <- refA.get
    } yield (result, a)

    run(op.map {
      case (result, a) =>
        assertEquals(result, true)
        assertEquals(a, Foo(1, -1))
    })
  }

  test("tryUpdate - fails to modify original value if it's already been modified concurrently") {
    val updateRefUnsafely: Ref[IO, Integer] => Unit = _.set(5).unsafeRunSync()

    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      result <- refB.tryUpdate { currentValue =>
        updateRefUnsafely(refB)
        currentValue + 1
      }
      a <- refA.get
    } yield (result, a)

    run(op.map {
      case (result, a) =>
        assertEquals(result, false)
        assertEquals(a, Foo(5, -1))
    })
  }

  test("tryModify - successfully modifies underlying Ref") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      result <- refB.tryModify(bar => (bar + 1, "A"))
      a <- refA.get
    } yield (result, a)

    run(op.map {
      case (result, a) =>
        assertEquals(result, Some("A"))
        assertEquals(a, Foo(1, -1))
    })
  }

  test("tryModify - fails to modify original value if it's already been modified concurrently") {
    val updateRefUnsafely: Ref[IO, Integer] => Unit = _.set(5).unsafeRunSync()

    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      result <- refB.tryModify { currentValue =>
        updateRefUnsafely(refB)
        (currentValue + 1, 10)
      }
      a <- refA.get
    } yield (result, a)

    run(op.map {
      case (result, a) =>
        assertEquals(result, None)
        assertEquals(a, Foo(5, -1))
    })
  }

  test("tryModifyState - successfully modifies underlying Ref") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      result <- refB.tryModifyState(State.apply(x => (x + 1, "A")))
      a <- refA.get
    } yield (result, a)

    run(op.map {
      case (result, a) =>
        assertEquals(result, Some("A"))
        assertEquals(a, Foo(1, -1))
    })
  }

  test("modifyState - successfully modifies underlying Ref") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      result <- refB.modifyState(State.apply(x => (x + 1, "A")))
      a <- refA.get
    } yield (result, a)

    run(op.map {
      case (result, a) =>
        assertEquals(result, "A")
        assertEquals(a, Foo(1, -1))
    })
  }

  test("access - successfully modifies underlying Ref") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      valueAndSetter <- refB.access
      (value, setter) = valueAndSetter
      success <- setter(value + 1)
      a <- refA.get
    } yield (success, a)
    run(op.map {
      case (success, a) =>
        assertEquals(success, true)
        assertEquals(a, Foo(1, -1))
    }.void)
  }

  test("access - successfully modifies underlying Ref after A is modified without affecting B") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      valueAndSetter <- refB.access
      (value, setter) = valueAndSetter
      _ <- refA.update(_.copy(baz = -2))
      success <- setter(value + 1)
      a <- refA.get
    } yield (success, a)
    run(op.map {
      case (success, a) =>
        assertEquals(success, true)
        assertEquals(a, Foo(1, -2))
    }.void)
  }

  test("access - setter fails to modify underlying Ref if value is modified before setter is called") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      valueAndSetter <- refB.access
      (value, setter) = valueAndSetter
      _ <- refA.set(Foo(5, -1))
      success <- setter(value + 1)
      a <- refA.get
    } yield (success, a)

    run(op.map {
      case (success, result) =>
        assertEquals(success, false)
        assertEquals(result, Foo(5, -1))
    }.void)
  }

  test("access - setter fails the second time") {
    val op = for {
      refA <- Ref[IO].of(Foo(0, -1))
      refB = Ref.lens[IO, Foo, Integer](refA)(Foo.get, Foo.set)
      valueAndSetter <- refB.access
      (_, setter) = valueAndSetter
      result1 <- setter(1)
      result2 <- setter(2)
      a <- refA.get
    } yield (result1, result2, a)

    run(op.map {
      case (result1, result2, a) =>
        assertEquals(result1, true)
        assertEquals(result2, false)
        assertEquals(a, Foo(1, -1))
    }.void)
  }

}
