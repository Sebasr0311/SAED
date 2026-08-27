import os
import re

directories = [
    "src/main/java/com/saed/backend/convivencia/service/impl",
    "src/main/java/com/saed/backend/convivencia/repository/impl",
    "src/main/java/com/saed/backend/convivencia/controller"
]

def fix_lombok(filepath):
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    if "@RequiredArgsConstructor" in content:
        content = content.replace("import lombok.RequiredArgsConstructor;\n", "")
        content = content.replace("@RequiredArgsConstructor\n", "")
        
        # Find class name
        class_match = re.search(r'public class (\w+)', content)
        if class_match:
            class_name = class_match.group(1)
            
            # Find private final field
            field_match = re.search(r'private final ([\w<>]+) (\w+);', content)
            if field_match:
                ftype = field_match.group(1)
                fname = field_match.group(2)
                
                constructor = f"""
    public {class_name}({ftype} {fname}) {{
        this.{fname} = {fname};
    }}
"""
                content = content.replace(f"private final {ftype} {fname};", f"private final {ftype} {fname};\n{constructor}")
                
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
            print(f"Fixed {filepath}")

for d in directories:
    for file in os.listdir(d):
        if file.endswith(".java"):
            fix_lombok(os.path.join(d, file))

