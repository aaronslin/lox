package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

class Interpreter implements Expr.Visitor<Object>,
                             Statement.Visitor<Void> {
  Interpreter() {
    this.currentScope = new Scope(null);
    this.executionStack = new Stack<>();
    this.callStack = new Stack<>();

    this.currentScope._declare("clock", NativeFunctions.CLOCK);
    this.currentScope._declare("assert", NativeFunctions.ASSERT);
    this.currentScope._declare("assert_raises", NativeFunctions.ASSERT_RAISES);
  }

  final static int MAX_RECURSION_DEPTH = 50;

  Scope currentScope;
  Stack<Statement> executionStack;
  Stack<LoxCallable> callStack;

  void interpret(Statement statement) {
    try {
      execute(statement);
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    } catch (AssertionError error) {
      Lox.assertionError(error);
    } catch (RuntimeException error) {
      DebugInfo debugInfo = new DebugInfo(this);
      Lox.javaError(new JavaError(statement, error), debugInfo);
    } finally {
      executionStack.clear();
    }
  }

  protected Object evaluate(Expr expr) {
    return expr.evaluateWith(this);
  }

  protected void execute(Statement stmt) {
    executionStack.push(stmt);
    stmt.executeWith(this);
    executionStack.pop();
  }

  static double toNum(Object obj) throws InterpreterCastException {
    if (obj == null) {
      return 0.0;
    } else if (obj instanceof Double) {
      return (double) obj;
    } else if (obj instanceof Boolean) {
      return ((boolean) obj ? 1.0 : 0.0);
    } else {
      throw new InterpreterCastException("double", obj);
    }
  }

  static boolean toBool(Object obj) {
    if (obj == null) {
      return false;
    } else if (obj instanceof Double) {
      return ((double) obj) != 0;
    } else if (obj instanceof Boolean) {
      return (boolean) obj;
    } else if (obj instanceof String) {
      return !obj.equals("");
    }
    return true;
  }
  
  static Object _add(Object leftObj, Object rightObj) throws InterpreterCastException {
    if (leftObj instanceof String && rightObj instanceof String) {
      return (String) leftObj + (String) rightObj;
    } else {
      return toNum(leftObj) + toNum(rightObj);
    }
  }

  static boolean _equals(Object leftObj, Object rightObj) {
    if (leftObj == null && rightObj == null) {
      return true;
    } else if (leftObj == null || rightObj == null) {
      return false;
    }
    return leftObj.equals(rightObj);
  }

  @Override
  public Object evalEmptyExpr(Empty empty) {
    return null;
  }

  @Override
  public Object evalBinaryExpr(Binary binary) {
    Object leftVal = evaluate(binary.left);
    Object rightVal = evaluate(binary.right);
    
    try {
      switch(binary.operator.type) {
        // Supported: numerical, except PLUS
        case SLASH: 
          return toNum(leftVal) / toNum(rightVal);
        case STAR: 
          return toNum(leftVal) * toNum(rightVal);
        case PLUS: 
          return _add(leftVal, rightVal);
        case MINUS: 
          return toNum(leftVal) - toNum(rightVal);

        // Supported: numerical
        case GREATER_EQUAL:
          return toNum(leftVal) >= toNum(rightVal);
        case LESS_EQUAL: 
          return toNum(leftVal) <= toNum(rightVal);
        case GREATER: 
          return toNum(leftVal) > toNum(rightVal);
        case LESS: 
          return toNum(leftVal) < toNum(rightVal);

        // Supported: everything. Note that 1!=true
        case BANG_EQUAL: 
          return !_equals(leftVal, rightVal);
        case EQUAL_EQUAL: 
          return _equals(leftVal, rightVal);
        default:
          throw _runtimeError(binary.operator, "Binary operator is not supported.");
      }
    } catch (InterpreterCastException e) {
      throw _runtimeError(binary.operator, e.getMessage());
    }
  }

  @Override
  public Object evalUnaryExpr(Unary unary) {
    Object exprValue = evaluate(unary.expr);
    try {
      if (unary.operator.type == TokenType.BANG) {
        return !toBool(exprValue);
      } else if (unary.operator.type == TokenType.MINUS) {
        return -toNum(exprValue);
      } else {
        throw _runtimeError(unary.operator, "Unary operator is not supported.");
      }
    } catch (InterpreterCastException e) {
      throw _runtimeError(unary.operator, e.getMessage());
    }
  }

  @Override
  public Object evalGroupingExpr(Grouping grouping) {
    return evaluate(grouping.expr);
  }

  @Override
  public Object evalLiteralExpr(Literal literal) {
    return literal.value;
  }

  @Override
  public Object evalLogicalExpr(Logical logical) {
    Object leftVal = evaluate(logical.left);
    if (logical.operator.type == TokenType.AND) {
      if (!toBool(leftVal)) return leftVal;
    } else if (logical.operator.type == TokenType.OR) {
      if (toBool(leftVal)) return leftVal;
    } else {
      throw _runtimeError(logical.operator, "Logical operator is not supported.");
    }
    return evaluate(logical.right);
  }

  @Override
  public Object evalVarExpr(Var varName) {
    Token name = varName.name;
    try {
      return currentScope.get(name);
    } catch (EnvironmentException e) {
      throw _runtimeError(name, String.format("Variable '%s' not defined.", name));
    }
  }
  
  @Override
  public Object evalAssignExpr(Assign assign) {
    if (assign.assignee instanceof Var) {
      Var assignee = (Var) assign.assignee;
      Object value = evaluate(assign.value);

      try {
        currentScope.assign(assignee.name, value);
      } catch (EnvironmentException e) {
        throw _runtimeError(assignee.name, "Undeclared variable cannot be assigned to.");
      }
      return value;
    } else if (assign.assignee instanceof Property) {
      Property assignee = (Property) assign.assignee;
      Object _propertyLeft = evaluate(assignee.left);

      if (!(_propertyLeft instanceof Fieldable)) {
        throw _runtimeError(assignee.right, String.format("Cannot get property '%s' of non-class.", assignee.right.literal));
      }

      Fieldable propertyLeft = (Fieldable) _propertyLeft;
      Object value = evaluate(assign.value);

      try {
        propertyLeft.setField(assignee.right, value);
      } catch (EnvironmentException e) {
        throw _runtimeError(assignee.right, String.format("Attribute '%s' cannot be assigned to.", assignee.right.lexeme));
      }
      return value;
    } else {
      throw _runtimeError(assign.token, "Invalid assignment target.");
    }
  }

  @Override
  public Object evalCallExpr(Call call) {
    Object callee = evaluate(call.callee);
    if (!(callee instanceof LoxCallable)) {
      throw _runtimeError(call.token, "Expression is not callable.");
    }

    LoxCallable loxCallable = (LoxCallable) callee;
  
    // Evaluate arguments in outer scope
    if (!loxCallable.isValidArity(call.arguments.size())) {
      throw _runtimeError(call.token, String.format("Expected %s arguments, but got %s.", loxCallable.arityString(), call.arguments.size()));
    }
    List<Object> args = evalSeries(call.arguments);
    
    // Check for maximum recursion depth.
    // It would be cleaner for this to be called on every LoxCallable call.
    // Right now, this check has to be done wherever `.call()` is invoked directly.
    if (callStack.size() > MAX_RECURSION_DEPTH) {
      throw _runtimeError(
        call.token, 
        String.format("Maximum recursion depth exceeded: %s", MAX_RECURSION_DEPTH)
      );
    }

    // Call the function.
    Scope outerScope = currentScope;
    callStack.push(loxCallable);
    try {
      return loxCallable.call(this, args);
    } finally {
      callStack.pop();
      currentScope = outerScope;
    }
  }

  @Override
  public Object evalPropertyExpr(Property property) {
    Object left = evaluate(property.left);

    try {
      return ((Fieldable) left).getField(property.right);
    } catch (ClassCastException e) {
      throw _runtimeError(
        property.right, 
        String.format("Cannot get property '%s' of non-class.", property.right.literal)
      );
    } catch (EnvironmentException e) {
      throw _runtimeError(
        property.right, 
        String.format("Attribute '%s' not found.", property.right.literal)
      );
    }
  }

  @Override
  public Object evalThisExpr(This expr) {
    try {
      LoxInstance owner = ((LoxMethod) callStack.peek()).owner;
      if (owner != null) {
        return owner;
      }
      throw _runtimeError(expr.token, "Can only call 'this' on bound methods.");
    } catch (RuntimeException e) {
      throw _runtimeError(expr.token, "Cannot call 'this' outside of an object method.");
    }
  }

  public <T extends Expr> List<Object> evalSeries(Series<T> series) {
    List<Object> values = new ArrayList<>();
    for (T item : series.members) { 
      values.add(evaluate(item));
    }
    return values;
  }

  /*
   o-----------------o
   | EXECUTE METHODS |
   o-----------------o
  */

  @Override
  public Void execExprStmt(ExprStmt stmt) {
    evaluate(stmt.expr);
    return null;
  }

  @Override
  public Void execPrintStmt(PrintStmt stmt) {
    System.out.println(evaluate(stmt.expr));
    return null;
  }

  @Override
  public Void execVarStmt(VarStmt stmt) {
    currentScope.declare(stmt.name, evaluate(stmt.expr));
    return null;
  }

  @Override
  public Void execBlockStmt(BlockStmt stmt) {
    Scope outerScope = currentScope;
    currentScope = new Scope(currentScope);
    try {
      for (Statement substmt : stmt.statements) {
        execute(substmt);
      }
    } finally {
      currentScope = outerScope;
    }
    return null;
  }

  @Override
  public Void execIfStmt(IfStmt stmt) {
    if (toBool(evaluate(stmt.condition))) {
      execute(stmt.then);
    } else {
      execute(stmt.otherwise);
    }
    return null;
  }

  @Override
  public Void execWhileStmt(WhileStmt stmt) {
    while (toBool(evaluate(stmt.condition))) {
      execute(stmt.body);
    }
    return null;
  }

  @Override
  public Void execForStmt(ForStmt stmt) {
    Scope outerScope = currentScope;
    currentScope = new Scope(currentScope);
    
    try {
      execute(stmt.initializer);
      while (toBool(evaluate(stmt.condition))) {
        if (stmt.body instanceof BlockStmt) {
          BlockStmt body = (BlockStmt) stmt.body;
          for (Statement substmt : body.statements) { 
            execute(substmt);
          }
        } else {
          execute(stmt.body);
        }
        execute(stmt.iterator);
      }
    } finally {
      currentScope = outerScope;
    }

    return null;
  }

  // Declares a function
  @Override
  public Void execFuncStmt(FuncStmt stmt) {
    LoxFunction func = new LoxFunction(stmt.name, stmt.parameters, stmt.body, currentScope);
    currentScope.declare(stmt.name, func);
    return null;
  }

  @Override
  public Void execReturnStmt(ReturnStmt stmt) {
    if (currentScope.parent == null) {
      throw _runtimeError(stmt.indicator, "Cannot return out of global scope.");
    }
    throw new ReturnException(evaluate(stmt.expr));
  }

  @Override
  public Void execClassStmt(ClassStmt stmt) {
    LoxClass loxClass;

    Scope outerScope = currentScope;
    try {
      loxClass = new LoxClass(stmt, this);
    } finally {
      currentScope = outerScope;
    }

    currentScope.declare(stmt.name, loxClass);
    return null;
  }

  private RuntimeError _runtimeError(Token token, String message) {
    return new RuntimeError(token, message).withInterpreterState(this);
  }
}
