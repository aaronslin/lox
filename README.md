

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
