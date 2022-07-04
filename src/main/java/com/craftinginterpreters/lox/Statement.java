package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

abstract class Statement extends Printable {
  // Token used for stack traces and line numbers. There ought to be a more 
  // methodical way to choose which token to associate with. 
  Token indicator;

  abstract public Void executeWith(Visitor<Void> visitor);
  
  interface Visitor<T> {
    public T execBlockStmt(BlockStmt stmt);
    public T execExprStmt(ExprStmt stmt);
    public T execClassStmt(ClassStmt stmt);
    public T execForStmt(ForStmt stmt);
    public T execFuncStmt(FuncStmt stmt);
    public T execIfStmt(IfStmt stmt);
    public T execPrintStmt(PrintStmt stmt);
    public T execReturnStmt(ReturnStmt stmt);
    public T execVarStmt(VarStmt stmt);
    public T execWhileStmt(WhileStmt stmt);
  }
}

class ExprStmt extends Statement {
  ExprStmt(Expr expr, Token indicator) {
    this.expr = expr;
    this.indicator = indicator;
    this._printables = Arrays.asList(expr);
  }

  final Expr expr;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execExprStmt(this);
  }
}

class PrintStmt extends Statement {
  PrintStmt(Expr expr, Token indicator) {
    this.expr = expr;
    this.indicator = indicator;
    this._printables = Arrays.asList(expr);
  }

  final Expr expr;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execPrintStmt(this);
  }
}

class VarStmt extends Statement {
  VarStmt(Token name, Expr expr, Token indicator) {
    this.name = name;
    this.expr = expr;
    this.indicator = indicator;
    this._printables = Arrays.asList(name, expr);
  }

  final Token name;
  final Expr expr;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execVarStmt(this);
  }
}

class BlockStmt extends Statement {
  BlockStmt(List<Statement> statements, Token indicator) {
    this.statements = statements;
    this.indicator = indicator;
    this._printables.addAll(statements);
  }

  final List<Statement> statements;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execBlockStmt(this);
  }
}

class WhileStmt extends Statement {
  WhileStmt(Expr condition, Statement body, Token indicator) {
    this.condition = condition;
    this.body = body;
    this.indicator = indicator;
    this._printables = Arrays.asList(condition, body);
  }
  
  final Expr condition;
  final Statement body;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execWhileStmt(this);
  }
}

class ForStmt extends Statement {
  ForStmt(
    Statement initializer, 
    Expr condition, 
    Statement iterator, 
    Statement body,
    Token indicator
  ) {
    this.initializer = initializer;
    this.condition = condition;
    this.iterator = iterator;
    this.body = body;
    this.indicator = indicator;
    this._printables = Arrays.asList(initializer, condition, iterator, body);
  }

  final Statement initializer;
  final Expr condition;
  final Statement iterator;
  final Statement body;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execForStmt(this);
  }
}

class IfStmt extends Statement {
  IfStmt(Expr condition, Statement then, Statement otherwise, Token indicator) {
    this.condition = condition;
    this.then = then;
    this.otherwise = otherwise;
    this.indicator = indicator;
    this._printables = Arrays.asList(condition, then, otherwise);
  }

  final Expr condition;
  final Statement then;
  final Statement otherwise;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execIfStmt(this);
  }
}

class ClassStmt extends Statement {
  ClassStmt(Token name, List<VarStmt> properties, List<FuncStmt> methods, Token indicator) {
    this.name = name;
    this.properties = properties;
    this.indicator = indicator;
    this.methods = methods;

    this._printables.add(name);
    this._printables.addAll(properties);
    this._printables.addAll(methods);
  }

  final Token name;
  final List<VarStmt> properties;
  final List<FuncStmt> methods;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execClassStmt(this);
  }
}

class FuncStmt extends Statement {
  FuncStmt(Token name, Series<Var> parameters, BlockStmt body, Token indicator) {
    this.name = name;
    this.parameters = parameters;
    this.body = body;
    this.indicator = indicator;
    this._printables = Arrays.asList(name, parameters, body);
  }

  final Token name;
  final Series<Var> parameters;
  final BlockStmt body;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execFuncStmt(this);
  }
}

class ReturnStmt extends Statement {
  ReturnStmt(Expr expr, Token indicator) {
    this.expr = expr;
    this.indicator = indicator;
    this._printables = Arrays.asList(expr);
  }

  final Expr expr;

  public Void executeWith(Statement.Visitor<Void> visitor) {
    return visitor.execReturnStmt(this);
  }
}


class Scope extends Printable {
  Scope(Scope parent) {
    this.parent = parent;
    this.locals = new HashMap<>();
    this._printables = Arrays.asList();
  }

  private Scope(Scope parent, Map<String, Variable> locals) {
    this.parent = parent;
    this.locals = locals;
    this._printables = Arrays.asList();
  }

  final Scope parent;
  final Map<String, Variable> locals;

  public Object get(Token token) {
    String name = token.literal.toString();
    if (locals.containsKey(name)) {
      return locals.get(name).value;
    }

    if (parent == null) {
      throw new EnvironmentException();
    }
    return parent.get(token);
  }

  void _declare(String name, Object value) {
    locals.put(name, new Variable(value));
  }

  public void declare(Token token, Object value) {
    _declare(token.literal.toString(), value);
  }

  public void assign(Token token, Object value) {
    String name = token.literal.toString();
    if (locals.containsKey(name)) {
      Variable variable = locals.get(name);
      variable.set(value);
    } else if (parent == null) {
      throw new EnvironmentException();
    } else {
      parent.assign(token, value);
    }
  }

  public Scope copyReferences() {
    // Creates scope with a `locals` map storing the same `(k,v)` pairs.
    // The map values reuse the same Variables.
    Map<String, Variable> newLocals = new HashMap<>(locals);
    return new Scope(parent, newLocals);
  }

  public Scope copyValues() {
    Map<String, Variable> newLocals = new HashMap<>();
    for (String name : locals.keySet()) {
      Object value = locals.get(name).value;
      newLocals.put(name, new Variable(value));
    }
    return new Scope(parent, newLocals);
  }

  public Scope getGlobal() {
    if (this.parent == null) {
      return this;
    }
    return parent.getGlobal();
  }

  @Override
  public void print() {
    StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
    System.out.println(caller.getClassName() + "." + caller.getMethodName());
    printScope("");
  }

  public void printScope(String prefix) {
    System.out.println(prefix + "Scope {");

    for (String key : locals.keySet()) {
      Variable v = locals.get(key);
      System.out.println(prefix + "  " + key);
      v.print(prefix + "  ", true);
    }

    if (parent != null) {
      parent.printScope(prefix + "  ");
    }
    System.out.println(prefix + "}");
  }
}

// This Variable class is useful if:
// 1. I want to do reference counting for GC
// 2. I want to distinguish empty initializer from nil;
class Variable extends Printable {
  Variable(Object value) {
    // Assign to the expression value, not the expression tree. Otherwise:
    //   var bar = 1;
    //   var foo = bar + 1;
    //   bar = 2;
    //   assert foo == 2;
    this.value = value;
    this._printables = Arrays.asList();
  }

  Object value;

  public void set(Object value) {
    this.value = value;
  }

  public String toString() {
    if (value == null) {
      return "null";
    }
    return "" + value + " (" + value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value)) + ")";
  }
}

/*
Scope: 
  Test case 1:
  var foo = 1;
  var bar = Cat();
  
  fun f(x, y) {
    assert x.addr != foo.addr
    assert y.addr == bar.addr
  }

  Test case 2: [different ways of setting].
  var a = 1;
  var b = Cat();
  var c = func();
  var d = a;  // should "evaluate" a, set equal to the variable's value
  var e = b;  // should set to same object as b
  var f = c;  // should not re-evaluate func.

  Test case 3:
  a = 1;
  if (...) {
    a=2;
    b=3;
  }
  func f () {
    a=2;
    b=3;
  }
  {
    a=2;
    b=3;
  }
  class Cat {
    a=2;
    b=3;
  }

Variables:
  Test case 1:
  var bar = 2;
  var foo = bar;
  var foo = 1;
  assert bar == 2;

  Test case 2:
  var bar = 2;
  var foo = bar;
  var bar = 1;
  assert foo == 2;  // 2 is primitive

  Test case 3:
  var bar = Cat(2);
  var foo = bar;
  var bar.age += 1;
  assert foo.age == 3;  // bar is object
  */

/*
program        → statement* EOF ;

statement      → exprStmt
               | printStmt 
               | assignStmt ;

exprStmt       → expression ";" ;
printStmt      → "print" expression ";" ;
assignStmt    -> var? literal (equal expression)?;
*/
