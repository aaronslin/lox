package com.craftinginterpreters.lox;

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

class LoxFunction implements LoxCallable {
  LoxFunction(FuncStmt funcStmt, Scope environment) {
    this.name = funcStmt.name.name.lexeme;
    this.parameters = funcStmt.parameters;
    this.body = funcStmt.body;
    this.environment = environment.createCopy();
  }

  final String name;
  final Series<Var> parameters;
  final BlockStmt body;
  final Scope environment;

  @Override
  public int arity() {
    return parameters.members.size();
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    interpreter.currentScope = new Scope(environment);

    try {
      // Initialize function parameters
      for (int i=0; i<arguments.size(); i++) {
        interpreter.currentScope.declare(
          parameters.get(i).name, 
          new Variable(arguments.get(i))
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
    return "<function " + name + ">";
  }
}
