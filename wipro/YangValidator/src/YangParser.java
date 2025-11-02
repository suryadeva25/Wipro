import model.YangModule;
import model.YangNode;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Stack;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class YangParser {
    private static final Pattern MODULE_PATTERN = Pattern.compile("^\\s*module\\s+(\\S+)\\s*\\{");
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^\\s*namespace\\s+\"([^\"]+)\"\\s*;");
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^\\s*prefix\\s+(\\S+)\\s*;");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+(\\S+)\\s*\\{");
    private static final Pattern CONTAINER_PATTERN = Pattern.compile("^\\s*container\\s+(\\S+)\\s*\\{");
    private static final Pattern LEAF_PATTERN = Pattern.compile("^\\s*leaf\\s+(\\S+)\\s*\\{");
    private static final Pattern LEAF_LIST_PATTERN = Pattern.compile("^\\s*leaf-list\\s+(\\S+)\\s*\\{");
    private static final Pattern LIST_PATTERN = Pattern.compile("^\\s*list\\s+(\\S+)\\s*\\{");
    private static final Pattern TYPE_PATTERN = Pattern.compile("^\\s*type\\s+(\\S+)\\s*;");
    private static final Pattern MANDATORY_PATTERN = Pattern.compile("^\\s*mandatory\\s+(true|false)\\s*;");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("^\\s*description\\s+\"([^\"]+)\"\\s*;");

    public YangModule parseYangFile(String filePath) throws IOException {
        YangModule module = null;
        Stack<YangNode> nodeStack = new Stack<>();
        Stack<String> braceStack = new Stack<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }
                
                // Parse module declaration
                if (module == null) {
                    Matcher moduleMatcher = MODULE_PATTERN.matcher(line);
                    if (moduleMatcher.find()) {
                        module = new YangModule(moduleMatcher.group(1));
                        braceStack.push("module");
                        continue;
                    }
                }
                
                if (module == null) continue;
                
                // Parse namespace
                Matcher namespaceMatcher = NAMESPACE_PATTERN.matcher(line);
                if (namespaceMatcher.find()) {
                    module.setNamespace(namespaceMatcher.group(1));
                    continue;
                }
                
                // Parse prefix
                Matcher prefixMatcher = PREFIX_PATTERN.matcher(line);
                if (prefixMatcher.find()) {
                    module.setPrefix(prefixMatcher.group(1));
                    continue;
                }
                
                // Parse imports
                Matcher importMatcher = IMPORT_PATTERN.matcher(line);
                if (importMatcher.find()) {
                    module.addImport(importMatcher.group(1));
                    braceStack.push("import");
                    continue;
                }
                
                // Parse container
                Matcher containerMatcher = CONTAINER_PATTERN.matcher(line);
                if (containerMatcher.find()) {
                    YangNode container = new YangNode(containerMatcher.group(1), "container");
                    if (nodeStack.isEmpty()) {
                        module.addNode(container);
                    } else {
                        nodeStack.peek().addChild(container);
                    }
                    nodeStack.push(container);
                    braceStack.push("container");
                    continue;
                }
                
                // Parse leaf
                Matcher leafMatcher = LEAF_PATTERN.matcher(line);
                if (leafMatcher.find()) {
                    YangNode leaf = new YangNode(leafMatcher.group(1), "leaf");
                    if (!nodeStack.isEmpty()) {
                        nodeStack.peek().addChild(leaf);
                    } else {
                        module.addNode(leaf);
                    }
                    nodeStack.push(leaf);
                    braceStack.push("leaf");
                    continue;
                }
                
                // Parse leaf-list
                Matcher leafListMatcher = LEAF_LIST_PATTERN.matcher(line);
                if (leafListMatcher.find()) {
                    YangNode leafList = new YangNode(leafListMatcher.group(1), "leaf-list");
                    if (!nodeStack.isEmpty()) {
                        nodeStack.peek().addChild(leafList);
                    } else {
                        module.addNode(leafList);
                    }
                    nodeStack.push(leafList);
                    braceStack.push("leaf-list");
                    continue;
                }
                
                // Parse list
                Matcher listMatcher = LIST_PATTERN.matcher(line);
                if (listMatcher.find()) {
                    YangNode list = new YangNode(listMatcher.group(1), "list");
                    if (!nodeStack.isEmpty()) {
                        nodeStack.peek().addChild(list);
                    } else {
                        module.addNode(list);
                    }
                    nodeStack.push(list);
                    braceStack.push("list");
                    continue;
                }
                
                // Parse type for current node
                if (!nodeStack.isEmpty()) {
                    Matcher typeMatcher = TYPE_PATTERN.matcher(line);
                    if (typeMatcher.find()) {
                        nodeStack.peek().setDataType(typeMatcher.group(1));
                        continue;
                    }
                    
                    // Parse mandatory
                    Matcher mandatoryMatcher = MANDATORY_PATTERN.matcher(line);
                    if (mandatoryMatcher.find()) {
                        nodeStack.peek().setMandatory("true".equals(mandatoryMatcher.group(1)));
                        continue;
                    }
                    
                    // Parse description
                    Matcher descMatcher = DESCRIPTION_PATTERN.matcher(line);
                    if (descMatcher.find()) {
                        nodeStack.peek().setDescription(descMatcher.group(1));
                        continue;
                    }
                }
                
                // Handle closing braces
                if (line.equals("}")) {
                    if (!braceStack.isEmpty()) {
                        String lastBrace = braceStack.pop();
                        if (!nodeStack.isEmpty() && 
                            ("container".equals(lastBrace) || "leaf".equals(lastBrace) || 
                             "leaf-list".equals(lastBrace) || "list".equals(lastBrace))) {
                            nodeStack.pop();
                        }
                    }
                }
            }
        }
        
        return module;
    }
    
    public void validateSyntax(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int braceCount = 0;
            int lineNumber = 0;
            boolean inModule = false;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }
                
                // Count braces for basic syntax validation
                for (char c : line.toCharArray()) {
                    if (c == '{') braceCount++;
                    if (c == '}') braceCount--;
                }
                
                // Check for module declaration
                if (MODULE_PATTERN.matcher(line).find()) {
                    inModule = true;
                }
                
                // Basic syntax checks
                if (line.contains(";") && !line.endsWith(";") && !line.endsWith(";;")) {
                    System.out.println("Warning: Line " + lineNumber + " - Semicolon might be misplaced");
                }
            }
            
            if (!inModule) {
                throw new IOException("No module declaration found in the file");
            }
            
            if (braceCount != 0) {
                throw new IOException("Unbalanced braces in YANG file");
            }
            
            System.out.println("✓ Basic syntax validation passed");
        }
    }
}