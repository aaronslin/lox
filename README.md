

# Using craftinginterpreter source:

```
mvn package
java -cp target/lox-interpreter-1.0-with-dependencies.jar com.craftinginterpreters.lox.Lox

         ^
         |_ should match the jar file
                                                          ^
                                                          |_ should match the package name
```                  


# Chapter Notes


### Ch. ???


### Ch. 6: 

The really cool thing is that this grammar can be unpacked very nicely into parsing code!
 * terminal (e.g. NUMBER or "/" ) = consume that token and advance
 * nonterminal (e.g. comparison) = a function call
 * ? or | = if/switch
 * (...)+ or (...)* = for/while

- left vs. right associativity
- concept of synchronization -- when a parser finds an error, it would be nice to continue and 
  report as many errors at once as possible. One can synchronize per-statement. If one statement
  throws an error, ignore everything else on that line and continue parsing on the next line.
- error reporting is surprisingly nontrivial!

### Ch. 7:

- Why is equality a different order of operation than comparison?
- "Each rule here only matches expressions at its precedence level or higher. " Hence:


/* 
Notes:
 - Here I execute an Expr tree. (As opposed to compiling to machine code or other options)
 - Lox is dynamically typed but Java is static!

Corrections:
0 / 0 (returns NaN, but should throw error)

Misleading error messages:
)
5 + "hi"
5 hi ("Cannot end with expression")
---- ("Must start with expression")

Feature differences:
 - the book's interpreter doesn't allow addition on booleans. 
 - the book's interpreter evaluates strings (even "") to true. Mine throws.
 - My interpreter lacks an interpret() method to map Java output values into
   Lox string that is shown to the user.
 - the book's interpreter uses the visitor pattern to write the evaluate()
   methods in the Interpreter, instead of scattering it into the Expr classes.
*/

### Ch. 8:
- Handle execute() Runtime errors
- Variable should just be an Expr
- Lox disallows `if (monday) var beverage = "espresso";` because the scope of beverage is ambiguous
  This is why they chose to create a declaration -> varDeclaration | statement
- Book also categorizes `var a = 1` as a statement (higher precedence than expression)
  but `a=1` as an expression.
- Recursive descent is nice. Essentially, "Parse everything higher precedence first. Then match on some token. 
  If the match misses, then let the lower precedence rules try matching."
- Book creates an Expr.Var(Token) -- note: not Expr.Var(Token, Object). There is a separate
  Stmt.Var(Token, Expr) for variable declarations -- note: not Stmt.Var(Token, Object).
  Finally, there is an Expr.Assign(Token, Expr) for assignments -- note: not Expr.Assign(String, Expr).
- environment.get has an interesting choice between throwing a compile error or runtime error.
  A compiler error would affect recursion and multi-function recursion
  because if f is defined before g, f cannot mention g. Hence runtime error.
- Mutation seems like a big topic (separate from assignment). Rust and Haskell opinionated about this.
- The single lookahead recursive descent parser is different for the two expressions: `a + b` vs. `a = 1`.
  The former "evaluates" everything that comes before the +, so it can do this:
     expression()
     consume(TokenType.PLUS);
     expression()
  However, `a=1` is different because we aren't evaluating a before assigning it.
    * Me: I'm not sure why this makes a difference, given that this lives in the parser, not the interpreter.

  Also assignment is right associative (a = (b = c)) while + is left associative ((a + b) + c).

- The book avoids looping to parse the left hand side of assignment expressions by taking advantage
  of the fact that every valid assignable LHS is also a valid standalone expression. (e.g. `Cat.name;` vs `Cat.name = ...`)
- The parser distinguishes between grouped and ungrouped expressions because `(a) = 3` is invalid.
- Static vs dynamic scope: See this: https://stackoverflow.com/questions/22394089/static-lexical-scoping-vs-dynamic-scoping-pseudocode
  Dynamic scope is determined by the runtime stack. Static scope is defined by the code block structure.
  Apparently static is faster than dynamic?

  My interpreter is written with dynamic scope, while the book uses static scope.
- Another huge implementation difference: assign and declaration are different because of nested environments:
  Declaration always declares in the innermost / current scope.
  Assignment walks the tree and can assign variables in outer scopes!

  If we support implicit variable declaration (e.g. no `var` needed), we have to decide how to handle.
   - Python: in current scope
   - Ruby: outer block if exists, else current scope
   - JS: outer block if exists, else global scope
- Book chooses to call { } a Stmt.Block. I didn't like this name because "block" is vague, but ig it's okay.
  Their code for parsing blocks is so much simpler than I imagined. 
    ```
    if(match(LEFT_BRACE)) return new Block(block());
    Block block() {
      while(!isAtEnd && !match(RIGHT_BRACE)) {
        statements.add(declaration());
      }
      throw "Unmatched }";
    }
    ```
- try... finally in Java. This way parent scope gets restored even with exceptions!
- also their executeBlock(Stmt, Environment), not executeBlock(Stmt)
- Interesting edge case: 

```
  var a = 1;
  {
    // I think this will NOT throw RuntimeError because (1) the decl will parse correctly (2) when initializing
    // it will find that `a` exists in outer scope but not current scope (3) then it will assign `1 + 2` to `a` in inner scope.
    var a = a + 2;
    print a;
  }
```

### Ch. 9:

- For loops would be more elegant to parse if it were instead `for(var i=0; i=i+1; i<10)`
- Dangling else problem
- forgot to short circuit `and` and `or`. Apparently their parser didn't parse it yet
- `and` has higher priority than `or`. Why? Probably because `and` ~ * and `or` ~ +
- Most surprisingly, `and` and `or` are not binary operators because they control logic flow too.
- Their for loop doesn't require the initializer to be a `varDecl`. In fact, their for loop parses to a WhileLoop. Clever

### Ch. 10:

- I was originally trying to do something like:

```
private <T> Series<T> series(_GetExpression<T> closure) { }
private Expr expression() { }
private interface _GetExpression<T> {
  T call();
}
series(() -> expression()); // cannot infer type-variables T
```

The reason this doesn't work is because the type inference system doesn't look inside of the lambda body to infer the type of T,
otherwise there is a risk of running into a loop. Consider the example below.
(https://stackoverflow.com/questions/31227149/why-is-this-type-inference-not-working-with-this-lambda-expression-scenario)

```
<T> T foo();
<T> void bar(FunctionalT t);
interface FunctionalT<T> {
  T call();
}

bar(() -> foo());
```

 - The book has precedence primary, callable, unary while mine is callable, primary, unary. I don't think this makes 
   a difference in this case because their match conditions are different, but maybe it makes sense to leave identifier as is in primary().
 - maximum arguments of 255
 - error vs. throwing
 - LoxCallable wraps a FuncStmt. The interface implements a call method, which will be reused later
 - executeBlock() is helpful
 - Every function must return something, even if it doesn't have a return. My implementation accidentally does this.
 - Looks like `return` outside of a function is not considered a parse error to them.
 - LOL at the return exception -- I did that as a hack but apparently that's the book's implementation choice too! 
    `super(null, null, false false)` to disable some exception machinery

 - to support local functions / closures, I think I'll have to first refactor the environments to be statically scoped instead of dynamically.
   implementation: Add a `closure` environment to each LoxCallable

 Main differences:
  - static (book implementation) vs. dynamic scope (my implementation)
  - book contains a native function `clock()`
  - book implementation of `LoxCallable` extends to classes, with an `arity()` and `call()` method.
  - my implementation throws a RuntimeError when executing `return` in global scope.
  - my implementation lacks a maximum argument length

### Ch. 11

- Book uses persistent data structures to split environments. Not sure how they're efficient, but they sound interesting.
- This is a really good observation: "We know static scope means that a variable usage always resolves to the same declaration, which can be determined just by looking at the text. Given that, why are we doing it dynamically every time? Doing so doesn’t just open the hole that leads to our annoying bug, it’s also needlessly slow."
- Book uses a resolver class, which occurs between parsing and interpreting, to dereference variable mappings.
  This is interesting because the resolver can be used for other static tasks, like type checking and what not.
  The main differences between resolution and interpreting is:
    * no side effects (no prints)
    * control flow is ignored (loops visited once, both if branches visited, etc.)
- Book counts the number of nested environment lookups once during resolution and turns it into a pointer dereference.
  So even if global <= block <= fun has 3-level lookup, the difference is that in the old code (ignoring correctness issues)
  it makes 3 hashmap lookups, which are slow in comparison to 3 pointer dereferences.
- Book treats this as a compile error (because it would be confusing for the `a`'s to refer to different things):

```
var a = "hi";
{
  var a = a;
}
```

This means that there is a moment during variable declaration when it is unavailable but declared.
- handling stray returns is done by the resolver, which answers an earlier question
- "Why is it safe to eagerly define the variable bound to a function’s name when other variables must wait until after they are initialized before they can be used?"
  Because variable declarations can be initialized to an expr that refers to the same variable name. In this case, it is ambiguous which scope that variable refers to.
  OTOH, a function body and parameters are always defined in a new scope created by the function, sidestepping the potential ambiguity.

### Ch. 12

- Good error handling system: Two parts: Parsing and Interpreting
  Parsing: "error at blah" should refer to the line with blah, not the preceding line.
  Interpreting: 
    So the interpreter should always have: line numbers, stacktrace. 
    Flag to print the environment info.
    Or just learn to use a java debugger?
    A way to print an object as it was typed in (not the Java object)

Raw notes:
```
/* 

def evaluate():
  value = Expr.visitExpr(expr)
  if (value instanceof LoxInstance) {
    lastGet = value;
  } else {
    lastGet = null;
  }


class Bird { var name = "[noname]"; }
fun f() {
  var c = Bird("Lita");
  return c.introduce;
}
var g = f();
g(); // prints Lita


// recency
a = Dog();
b = a.get_name;
c = Cat().identity(b); // reset lastGet
c(); // should be ~ a.get_name()

f(a.get_instance(), foo());

A method's owner is never changes, once instantiated.
But Interpreter also needs to know how to interpret `this` without knowing it's in a function call.
 ... or.... keep a call stack?

Strategy:
 - Interpreter keeps a call stack. `evalThis` looks at `currentFunc.owner -> LoxInstance or null`
 - LoxCallable: associate each with an `owner` at construction time. 
    - Classes, native functions, functions don't have owners.
    - Instances have different methods. Or methods can be wrapped to lookup the instance, then call.
 - Instantiating: For each method, rebind its owner to the new instance.
 - evalProperty/evalCall


 OR

 - When entering a callable, set currentMethod = foo, and foo.owner. (This /is/ a call stack.)

Class and object methods:
  x. when class method updates, the object method should too.
  y. but the method should operate on different objects.

3 cases for passing the object to the method:
LoxCallable(Interpreter, args) 
1. associate object with LoxCallable
  a. directly -- impossible because of (x)
  b. indiectly -- Method(ClassFunction(Environment), Owner)
2. associate object with Interpreter
  - tough to get a single global `currentObject` correct (if possible at all)
    - anything that returns a loxCallable must update it.
    - but it is not necessarily the most recent object. (see 'recency' test)
  - the object should "live with" the method.
3. associate it with args. out of question.

 
*/ 
```

##### Book notes
 
* Book takes approach of "instances are loose bags of data and you can add/remove fields as needed". Mine is a lot less flexible.
  This is why they use a `HashMap` to track fields instead of an `Environment`
* Book broke the problem into smaller pieces by first starting with classes/instances with no custom constructors
* Book has `((call '.')? IDENTIFIER '=' assignment) | or` for parsing the LHS of an assignment, while mine is just:
  `or ('=' assignment)?`. Surely these aren't equivalent. For example, `2 = 1` would be a runtime not parsing exception in my interpreter.
  I wonder if `(a) = 1` works for book's?
* Book also nicely introduces a `setter` object to represent assignment to an `a.b` (different from an assign). I think it's nice that
  their parser instead of their interpreter handles this.
* I fail this test case (Lua/JS prints "bill", Python prints "jane". Mine also prints "jane" because I bind `this` to the method.):

```
class Person {
  sayName() {
    print this.name;
  }
}

var jane = Person();
jane.name = "Jane";

var bill = Person();
bill.name = "Bill";

bill.sayName = jane.sayName;
bill.sayName(); // ?
```
  I did not fix this in my test cases because of the dilemma of whether `this` should be associated with the method or computed by the
  interpreter. I couldn't see a way to make it so that class method updates propagate to the instances, but instance method updates don't
  propagate to the class.

* When a `get` is called in book's implementation, it still looks up the class's method. This would pass the `test_instance_class_fields` test i.e.

Mine:

```
Class:
  |_ method
Instance1
  |_ new method
Instance2
  |_ new method
```

Book's:

```
Class:
Instance1:
Instance2:
  |_ method
```


* Book implements `this` very similar to how I originally conceived of it. You nest it into an environment so that it looks like below,
  and then add a `bind` method that wraps the original `LoxFunction` method in another `LoxFunction`. This is simpler than creating
  a LoxMethod.

```
global env
|_ env with only 'this'
   |_ local env
```

So all in all, this is the lifecycle of `cat.method()`:
  - when `Cat` is defined, the method definition is added to the _class's_ bag of methods.
  - when `cat.method` is looked-up, it finds `method` in the class's bag of methods, and binds it to `cat`.
    - binding effectively does this. This enables all instances to reuse the same class method, but isolating
      which objects they are bound to:
      ```
      def bind(method, instance):
        env = new Environment(method.environment);
        env.define("this", instance);
        return new LoxFunction(method, env);
      ```
  - `method()` is called, like any other closure.

* Book also has a precedence of fields over methods. So `get` just does 2 lookups, which is fine.
* Book avoids relying on a callstack to detect invalid uses of `this`, but detecting it at resolution time.
* Book avoids defining a default init, ... by simply skipping the `callConstructor` step if `init` isn't defined.
* Banning `init` calls:
  * Looks like they didn't have a great answer to how to ban outside `init` calls either. They just short-circuit
    the `LoxInstance.call` method if the name matches `init`. I just don't store it as a method.
  * Bans `return` from init.

Overall: I think I thought of most of the edge cases, and often made different choices from the book.
