javac -d classes src/com/craftinginterpreters/lox/*.java;
java -cp classes/ com.craftinginterpreters.lox.$1 $2 $3
