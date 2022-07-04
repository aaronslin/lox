package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
  private static final Interpreter interpreter = new Interpreter();
  static boolean hadError = false;
  static boolean hadRuntimeError = false;

  public static void main(String[] args) throws IOException {
    if (args.length > 1) {
      System.out.println("Usage: jlox [script]");
      System.exit(64); // [64]
    } else if (args.length == 1) {
      runFile(args[0]);
    } else {
      runPrompt();
    }
  }
  private static void runFile(String path) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(path));
    run(new String(bytes, Charset.defaultCharset()));

    // Indicate an error in the exit code.
    if (hadError) System.exit(65);

    // (alin) UNCOMMENT
    // if (hadRuntimeError) System.exit(70);
  }
  private static void runPrompt() throws IOException {
    InputStreamReader input = new InputStreamReader(System.in);
    BufferedReader reader = new BufferedReader(input);

    for (;;) { // [repl]
      System.out.print("> ");
      String line = reader.readLine();
      if (line == null) break;
      run(line);
      hadError = false;
    }
  }
  private static void run(String source) {
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();
    Parser parser = new Parser(tokens);
    List<Statement> statements = parser.parse();

    // Stop if there was a syntax error.
    if (hadError) return;
    
    // // parser debugging
    // for (Statement stmt : statements) {
    //   stmt.print();
    // }
    for (Statement stmt : statements) {
      interpreter.interpret(stmt);
    }
  }
  static void error(int line, String message) {
    report(line, "", message);
  }

  private static void report(int line, String where,
                             String message) {
    System.err.println(
        "[line " + line + "] Error" + where + ": " + message);
    hadError = true;
  }
  static void error(Token token, String message) {
    if (token.type == TokenType.EOF) {
      report(token.line, " at end", message);
    } else {
      report(token.line, " at '" + token.lexeme + "'", message);
    }
  }
  static void runtimeError(RuntimeError error) {
    System.err.println("\n[RUNTIME ERROR]");
    printDebugInfo(error.debugInfo);
    System.err.println(error.getMessage() +
        "\n[line " + error.token.line + "]");
    hadRuntimeError = true;
  }
  static void assertionError(AssertionError error) {
    System.err.println("\n[ASSERTION ERROR]");
    printDebugInfo(error.debugInfo);
    System.err.println(error.getMessage());
    hadRuntimeError = true;
  }
  // TODO: modify javaError to encapsulate debugInfo
  static void javaError(JavaError error, DebugInfo debugInfo) {
    System.err.println("\n[FATAL]");
    printDebugInfo(debugInfo);
    error.error.printStackTrace();
    System.err.println(error.error);
    hadRuntimeError = true;
  }

  static void printDebugInfo(DebugInfo debugInfo) {
    for (Statement stmt : debugInfo.executionStack) {
      System.err.printf("[line %s]\n", stmt.indicator.line);
      stmt.print();
    }

    System.err.println("Call stack:");
    for (LoxCallable callable : debugInfo.callStack) {
      System.err.println("  " + callable);
    }

    System.err.println("Environment:");
    debugInfo.environment.print();
  }
}
