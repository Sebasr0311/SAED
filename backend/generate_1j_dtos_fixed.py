import os
import re

base_dir = "src/main/java/com/saed/backend/convivencia/dto"

def remove_lombok(content):
    content = content.replace("import lombok.Data;\n", "")
    content = content.replace("@Data\n", "")
    return content

for file in os.listdir(base_dir):
    if file.endswith(".java"):
        filepath = os.path.join(base_dir, file)
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
        
        content = remove_lombok(content)
        
        fields = re.findall(r'private\s+([\w<>]+)\s+(\w+);', content)
        getters_setters = "\n"
        for ftype, fname in fields:
            cap_name = fname[0].upper() + fname[1:]
            getters_setters += f"    public {ftype} get{cap_name}() {{ return this.{fname}; }}\n"
            getters_setters += f"    public void set{cap_name}({ftype} {fname}) {{ this.{fname} = {fname}; }}\n"
            
        content = content.replace("}", getters_setters + "}")
        
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)

print("Lombok removed and getters/setters added")
