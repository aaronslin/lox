/* 
From ~/Documents/lox:

To compile:
$ mvn package 

To run:
$ java -cp target/lox-interpreter-1.0.jar lox.Lox

Old: 
$ javac -d . Lox.java
$ java lox.Lox
*/
package lox;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class Lox {
  static boolean hadError = false;
  static boolean shouldRun = true;

  public static void main(String[] args) throws IOException {
    final Thread mainThread = Thread.currentThread();
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        shouldRun = false;
      }
    });
    if (args.length > 1) {
      System.out.println("Usage: jlox [script]");
      System.exit(64); 
    } else if (args.length == 1) {
      runFile(args[0]);
    } else {
      runPrompt();
    }
  }

  private static void runFile(String path) throws IOException {
    byte[] bytes=Files.readAllBytes(Paths.get(path));
    // TODO: error handle properly
    run(new String(bytes, Charset.defaultCharset()));

    // Indicate an error in the exit code.
    if (hadError) System.exit(65);
  }

  private static void runPrompt() throws IOException {
    InputStreamReader input = new InputStreamReader(System.in);
    BufferedReader reader = new BufferedReader(input);
    int lineNumber = 1;

    while(shouldRun) {
      System.out.print("> ");
      String line = reader.readLine();
      if (line == null) break;
      try {
        run(line);
        hadError = false;
        lineNumber++;
      } catch(ScannerException e) {
        reportError(lineNumber, e.column, e.getMessage(), e.code);
        
      }
    }
  }

  private static void run(String source) {
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();

    // For now, just print the tokens.
    for (Token token : tokens) {
      System.out.println(token);
    }
  }

  private static void reportError(int line, int column, String where, String code) {
    String prefix = "[line " + line + "] Error" + where + ": ";
    System.err.println(prefix + code);
    if (column >= 0) {
      String errorHelper = " ".repeat(prefix.length() + column - 1) + "^";
      System.err.println(errorHelper);
    }
    hadError = true;
  }
}

class ScannerException extends RuntimeException {
  String code;
  int column;

  public ScannerException(String message, String code, int column) {
    super(message);
    this.code = code;
    this.column = column;
  }

  public ScannerException(String message) {
    this(message, "", -1);
  }
}

enum ScannerState {
  DEFAULT, 
  STRING,    // Inside a string
  STRING_ESCAPING, // Inside a string, and mid-escape sequence
  STRING_CLOSING, // Just closed a string with a close ".
  INTEGER,
  FLOAT,
  LITERAL,
  SYMBOL,
  SYMBOL_LONG,
  ERROR,
  UNCAUGHT_ERROR;
}

class Scanner {
  // Allowed symbols, ordered by ASCII value
  // Other remaining characters: space.
  static final String ALPHABET = "_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  static final String DIGIT = "0123456789";
  static final String SYMBOLS_DELIMITER = ",;";
  static final String SYMBOLS_COMPARISON = "!<=>";
  static final String SYMBOLS_OPERATION = "+-*/";
  static final String SYMBOLS_ENCLOSURE = "(){}";
  static final String SYMBOLS_OTHER = ".\"\\";

  static final String SYMBOLS = SYMBOLS_DELIMITER + SYMBOLS_COMPARISON + SYMBOLS_OPERATION + SYMBOLS_ENCLOSURE;
  static final String ALL_CHARS = ALPHABET + DIGIT + SYMBOLS + SYMBOLS_OTHER;

  static final Map<String, TokenType> symbolToTokenType = ImmutableMap.<String, TokenType>builder()
    .put("!", TokenType.NEGATE)
    .put("=", TokenType.ASSIGN)
    .put("<", TokenType.COMPARE)
    .put(">", TokenType.COMPARE)
    .put("+", TokenType.OPERATOR)
    .put("-", TokenType.OPERATOR)
    .put("*", TokenType.OPERATOR)
    .put("/", TokenType.OPERATOR)
    .put("(", TokenType.PAREN_OPEN)
    .put(")", TokenType.PAREN_CLOSE)
    .put("{", TokenType.BRACE_OPEN)
    .put("}", TokenType.BRACE_CLOSE)
    .put(".", TokenType.PERIOD)
    .put(",", TokenType.COMMA)
    .put(";", TokenType.SEMICOLON)
    .build();

  // State transitions in which the character should get appended to the buffer.
  static final Map<ScannerState, List<ScannerState>> validTransitions = ImmutableMap.of(
    ScannerState.DEFAULT, ImmutableList.of(),
    ScannerState.STRING, ImmutableList.of(ScannerState.STRING, ScannerState.STRING_ESCAPING, ScannerState.STRING_CLOSING),
    ScannerState.STRING_ESCAPING, ImmutableList.of(ScannerState.STRING),
    ScannerState.STRING_CLOSING, ImmutableList.of(),
    ScannerState.INTEGER, ImmutableList.of(ScannerState.INTEGER, ScannerState.FLOAT),
    ScannerState.FLOAT, ImmutableList.of(ScannerState.FLOAT),
    ScannerState.LITERAL, ImmutableList.of(ScannerState.LITERAL),
    ScannerState.SYMBOL, ImmutableList.of(ScannerState.SYMBOL_LONG),
    ScannerState.SYMBOL_LONG, ImmutableList.of()
  );

  // Mappings of state to token type. Single char SYMBOL's must be handled separately.
  static final Map<ScannerState, TokenType> stateToTokenType = ImmutableMap.of(
    ScannerState.DEFAULT, TokenType.ERROR,
    ScannerState.STRING, TokenType.ERROR,
    ScannerState.STRING_ESCAPING, TokenType.ERROR,
    ScannerState.STRING_CLOSING, TokenType.STR,
    ScannerState.INTEGER, TokenType.INT,
    ScannerState.FLOAT, TokenType.FLOAT,
    ScannerState.LITERAL, TokenType.VAR,
    ScannerState.SYMBOL, TokenType.ERROR,
    ScannerState.SYMBOL_LONG, TokenType.COMPARE
  );

  static final Set<String> reservedWords = ImmutableSet.<String>builder()
    .put("and")
    .put("class")
    .put("else")
    .put("false")
    .put("fun")
    .put("for")
    .put("if")
    .put("nil")
    .put("or")
    .put("print")
    .put("return")
    .put("super")
    .put("this")
    .put("true")
    .put("var")
    .put("while")
    .build();

  String source;

  public Scanner(String source) {
    this.source = source;
  }

  private static String oneOf(String allowedChars) {
    return "[" + allowedChars + "]";
  }
    
  public List<Token> scanTokens() {
    List<Token> tokenList = new ArrayList<>();
    ScannerState state = ScannerState.DEFAULT;

    // Initialize the buffer as if the previous character was a space.
    String buffer = " ";

    // Iterate over the characters in the source string.
    for (int i=0; i < source.length(); i++) {
      char nextChar = source.charAt(i);
      if (state == ScannerState.STRING && ALL_CHARS.indexOf(nextChar) < 0) {
        throw new ScannerException(String.format("Error: %s is an invalid character.", nextChar), source, i);
      }

      ScannerState nextState = getNextState(state, buffer, nextChar);
      // System.out.printf("debug : %s\t%s\t%s\t-> %s\n", state, buffer, nextChar, nextState);

      if (nextState == ScannerState.UNCAUGHT_ERROR || nextState == ScannerState.ERROR) {
        System.out.printf(
          "%s: char %s cannot follow %s with state %s.", 
          nextState, 
          nextChar, 
          buffer,
          state
        );
        throw new ScannerException("Error: getNextState", source, i);
      }

      // Determine whether to append to existing buffer, or to create a new token.
      if (!validTransitions.containsKey(state)) {
        // I did something wrong
        throw new ScannerException(String.format("Error: validTransitions does not contain state %s as a key.", state), source, i);
      } else if (validTransitions.get(state).contains(nextState)) {
        buffer += nextChar;
      } else {
        createAndAddToken(tokenList, state, buffer, i);
        buffer = String.valueOf(nextChar);
      }
      state = nextState;
    }

    // Create token from remaining characters in the buffer.
    createAndAddToken(tokenList, state, buffer, source.length());
    return tokenList;
  }

  private void createAndAddToken(List<Token> tokenList, ScannerState state, String buffer, int column) {
    if (!buffer.equals(" ")) {
      try {
        Token token = createToken(state, buffer);
        tokenList.add(token);
      } catch(ScannerException e) {
        throw new ScannerException(e.getMessage(), source, column);
      }
    }
  }

  private static Token createToken(ScannerState state, String buffer) {
    // TODO: revisit this
    TokenType tokenType;
    if (state == ScannerState.SYMBOL) {
      if (!symbolToTokenType.containsKey(buffer)) {
        // I did something wrong
        throw new ScannerException(String.format("Error: symbolToTokenType does not contain '%s' as a key.", buffer));
      }
      tokenType = symbolToTokenType.get(buffer);
    } else {
      tokenType = stateToTokenType.get(state);
      if (tokenType == TokenType.VAR && reservedWords.contains(buffer)) {
        tokenType = TokenType.KEYWORD;
      }
    }
    return new Token(buffer, tokenType);
  }

  private static ScannerState getNextState(ScannerState currentState, String buffer, char currentChar) {
    switch (currentState) {
      case UNCAUGHT_ERROR:
        return ScannerState.UNCAUGHT_ERROR;
      case STRING:
        // Next possible states: STRING, STRING_ESCAPING, STRING_CLOSING
        // Assumes currentChar isn't like a dumb emoji or Chinese character
        // TODO: Create a master string with all allowed characters, to avoid $, & etc.
        if (currentChar == '\"') {
          return ScannerState.STRING_CLOSING;
        } else if (currentChar == '\\') {
          return ScannerState.STRING_ESCAPING;
        } else {
          return ScannerState.STRING;
        }
      case STRING_ESCAPING:
        // Next possible states: STRING, ERROR
        if ("\"\\".indexOf(currentChar) >= 0) {
          return ScannerState.STRING;
        } else {
          return ScannerState.ERROR;
        }
    }
    
    if (currentChar == ' ') {
      return ScannerState.DEFAULT;
    }

    switch (currentState) {
      case STRING_CLOSING:
        // Next possible states: SYMBOL, ERROR
        // TODO: check that next char is operation, space, close_paren, maybe others
        if (SYMBOLS.indexOf(currentChar) >= 0) {
          return ScannerState.SYMBOL;
        }
        return ScannerState.ERROR;
      case INTEGER:
        // Next possible states: ERROR, DEFAULT, INTEGER, FLOAT, SYMBOL
        if (currentChar == '.') {
          return ScannerState.FLOAT;
        } else if (DIGIT.indexOf(currentChar) >= 0) {
          return ScannerState.INTEGER;
        } else if (SYMBOLS.indexOf(currentChar) >= 0) {
          return ScannerState.SYMBOL;
        } else if ((ALPHABET + "\"\\").indexOf(currentChar) >= 0) {
          return ScannerState.ERROR;
        } else {
          return ScannerState.UNCAUGHT_ERROR;
        }
      case FLOAT:
        // Next possible states: ERROR, DEFAULT, FLOAT, SYMBOL
        if (DIGIT.indexOf(currentChar) >= 0) {
          return ScannerState.FLOAT;
        } else if (SYMBOLS.indexOf(currentChar) >= 0) {
          return ScannerState.SYMBOL;
        } else if ((ALPHABET + ".\"\\").indexOf(currentChar) >= 0) {
          return ScannerState.ERROR;
        } else {
          return ScannerState.UNCAUGHT_ERROR;
        }
      case LITERAL:
        // Next Possible states: DEFAULT, ERROR, LITERAL, SYMBOL
        if ((ALPHABET + DIGIT).indexOf(currentChar) >= 0) {
          return ScannerState.LITERAL;
        } else if ((SYMBOLS + ".").indexOf(currentChar) >= 0) {
          return ScannerState.SYMBOL;
        } else if ("\"\\".indexOf(currentChar) >= 0) {
          return ScannerState.ERROR;
        } else {
          return ScannerState.UNCAUGHT_ERROR;
        }

      // SYMBOL, SYMBOL_LONG, and DEFAULT have similar casework. Hence no `break`s.
      case SYMBOL:
        // Next Possible states: DEFAULT, STRING, INTEGER, LITERAL, SYMBOL, SYMBOL_LONG, ERROR
        if (currentChar == '=' && buffer.matches(oneOf(SYMBOLS_COMPARISON))) {
          return ScannerState.SYMBOL_LONG;
        }
      case SYMBOL_LONG:
      case DEFAULT:
        if (currentChar == '\"') {
          return ScannerState.STRING;
        } else if (DIGIT.indexOf(currentChar) >= 0) {
          return ScannerState.INTEGER;
        } else if (ALPHABET.indexOf(currentChar) >= 0) {
          return ScannerState.LITERAL;
        } else if (SYMBOLS.indexOf(currentChar) >= 0) {
          return ScannerState.SYMBOL;
        } else if (".\\".indexOf(currentChar) >= 0) {
          return ScannerState.ERROR;
        } else {
          return ScannerState.UNCAUGHT_ERROR;
        }
    }

    return ScannerState.UNCAUGHT_ERROR;
  }
}

enum TokenType {
  // tokens derived from states
  STR,
  VAR,
  INT,
  FLOAT,
  COMPARE,
  KEYWORD,

  // tokens derived from symbols
  NEGATE,
  ASSIGN,
  OPERATOR,
  PAREN_OPEN,
  PAREN_CLOSE,
  BRACE_OPEN,
  BRACE_CLOSE,
  PERIOD,
  COMMA,
  SEMICOLON,

  EOF,

  // other
  ERROR;
}

class Token {
  String tokenValue;
  TokenType tokenType;
  
  public Token(String tokenValue, TokenType tokenType) {
    this.tokenValue = tokenValue;
    this.tokenType = tokenType;
  }

  public String toString() {
    return String.format("Token(%s, %s)", tokenValue, tokenType);
  }
}

/*
Next steps:
 * test the scanner! see if it works!
 * compare to Ch. 4 implementation.
    * uses lookahead instead of this FSM style parsing. uses advance() to consume a char and peek() to lookahead once
    * EOF tokenType
    * \n, \t, \r should be in DEFAULT
    * handling comments with //
    * storing line number with Token. And something called "literal"
      * literal is "123". value is 123.
    * 123. should not be allowed as a float because 123.sqrt() gets weird
*/
