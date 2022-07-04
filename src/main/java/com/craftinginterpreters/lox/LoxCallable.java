package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.List;

interface LoxCallable {
  int arity();
  Object call(Interpreter interpreter, List<Object> arguments);

  default boolean isValidArity(int numArgs) {
    return arity() == numArgs;
  }

  default String arityString() {
    return "" + arity();
  }
}

abstract class Fieldable extends Printable {
  Scope fields;

  public Object getField(Token token) {
    return fields.get(token);
  }

  public void setField(Token token, Object value) {
    fields.assign(token, value);
  }
}

class LoxFunction implements LoxCallable {
  LoxFunction(Token token, Series<Var> parameters, BlockStmt body, Scope environment) {
    this.token = token;
    this.parameters = parameters;
    this.body = body;
    this.environment = environment.copyReferences();
  }

  final Token token;
  final Series<Var> parameters;
  final BlockStmt body;
  final Scope environment;

  @Override
  public int arity() {
    return parameters.members.size();
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    // Set the calling scope. It is the caller's responsibility to reset the scope.
    interpreter.currentScope = new Scope(environment);
    
    // Recursion depth is checked for user-defined LoxFunctions but `callStack` is set by evalCall
    // for all LoxCallables. hmm...

    try {
      // Initialize function parameters
      for (int i=0; i<arguments.size(); i++) {
        interpreter.currentScope.declare(
          parameters.get(i).name, 
          arguments.get(i)
        );
      }

      // Execute function body
      for (Statement stmt : body.statements) {
        interpreter.execute(stmt);
      }
      return null;
    } catch (ReturnException e) {
      return e.value;
    }
  }

  public String toString() {
    return "<function " + token.lexeme + ">";
  }
}

class LoxMethod implements LoxCallable {
  /* 
  A LoxMethod is a function with an associated object that "owns" it. 
  Because class method updates must propagate to instances, the `owner` cannot
  be directly associated with the LoxFunction. Hence, the LoxMethod.
  
  EDIT: nevermind. This abstraction doesn't do anything. See
  `test_instance_class_fields` for what this was originally intended to achieve.
  The book's jlox doesn't even support class methods, so I've decided to
  pass on ironing out this test case.
  */

  LoxMethod(LoxFunction function, LoxInstance owner) {
    this.function = function;
    this.owner = owner;
  }

  // Owner should be "final" in the sense that once determined, it should not change.
  // However, in cannot always be determined in the LoxFunction constructor, because
  // `init` acts like an instance method, not a class method.
  final LoxInstance owner;
  final LoxFunction function;

  @Override
  public int arity() {
    return function.arity();
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    return function.call(interpreter, arguments);
  }

  public String toString() {
    return "<method " + function.token.lexeme + " bound to " + owner + ">";
  }
}

class LoxClass extends Fieldable implements LoxCallable {
  LoxClass(ClassStmt classStmt, Interpreter interpreter) {
    this.token = classStmt.name;
    this.environment = interpreter.currentScope.copyReferences();
    this.fields = new Scope(null);

    // Set a default constructor. Not callable by callers.
    LoxCallable _constructor = new LoxCallable() {
      @Override
      public int arity() { return 0; }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        return null;
      }
    };

    interpreter.currentScope = new Scope(environment);

    for (VarStmt stmt : classStmt.properties) {
      fields.declare(stmt.name, interpreter.evaluate(stmt.expr));
    }
    for (FuncStmt stmt : classStmt.methods) {
      LoxFunction method = new LoxFunction(stmt.name, stmt.parameters, stmt.body, environment);

      if (stmt.name.lexeme.equals(INIT)) {
        _constructor = method;
      } else {
        fields.declare(stmt.name, method);
      }
    };
    
    this.constructor = _constructor;
  }

  final static String INIT = "init";

  final Token token;
  final Scope environment;
  final LoxCallable constructor;

  @Override
  public int arity() {
    return constructor.arity();
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    interpreter.currentScope = new Scope(environment);
    LoxInstance instance = new LoxInstance(this);
    instance.initialize(interpreter, arguments);
    return instance;
  }

  public String toString() {
    return "<class " + token.lexeme + ">";
  }
}

class LoxInstance extends Fieldable {
  LoxInstance(LoxClass loxClass) {
    this.classToken = loxClass.token;
    this.fields = loxClass.fields.copyValues();

    // Bind all class methods to their instances so that `this` works correctly.
    // This implementation is kind of gross, but it works. ¯\_(ツ)_/¯
    LoxCallable _constructor = loxClass.constructor;
    if (_constructor instanceof LoxFunction) {
      _constructor = new LoxMethod(((LoxFunction) _constructor), this);
    }
    this.constructor = _constructor;

    for (String name : this.fields.locals.keySet()) {
      Object value = this.fields.locals.get(name).value;
      if (value instanceof LoxFunction) {
        LoxFunction classMethod = (LoxFunction) value;
        this.fields._declare(name, new LoxMethod(classMethod, this));
      }
    }
    
    this._printables = Arrays.asList(fields);
  }
  final Token classToken;
  final LoxCallable constructor;

  public String toString() {
    return "<" + classToken.lexeme + "@" + Integer.toHexString(System.identityHashCode(this)) + ">";
  }

  public void initialize(Interpreter interpreter, List<Object> arguments) {
    interpreter.callStack.push(constructor);
    try {
      constructor.call(interpreter, arguments);
    } catch (ReturnException e) {
      // Do nothing if the constructor returns.
    } finally {
      interpreter.callStack.pop();
    }
  }
}
