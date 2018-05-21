javac -d classes src/com/craftinginterpreters/tool/*.java;
java -cp classes/ com.craftinginterpreters.tool.GenerateAst $1
