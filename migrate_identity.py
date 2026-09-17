import os
import shutil

base_dir = r"C:\Users\LearnersYT\source\TheDesiTadka\Mobile"

# 1. Update app/build.gradle.kts
app_build_gradle = os.path.join(base_dir, "app", "build.gradle.kts")
with open(app_build_gradle, "r", encoding="utf-8") as f:
    content = f.read()
content = content.replace('namespace = "com.streamhub.app"', 'namespace = "com.thedesitadka.app"')
content = content.replace('applicationId = "com.streamhub.app"', 'applicationId = "com.thedesitadka.app"')
with open(app_build_gradle, "w", encoding="utf-8") as f:
    f.write(content)
print("Updated app/build.gradle.kts")

# 2. Update proguard-rules.pro
proguard_path = os.path.join(base_dir, "app", "proguard-rules.pro")
with open(proguard_path, "r", encoding="utf-8") as f:
    content = f.read()
content = content.replace("com.streamhub.", "com.thedesitadka.")
with open(proguard_path, "w", encoding="utf-8") as f:
    f.write(content)
print("Updated proguard-rules.pro")

# 3. Update AndroidManifest.xml
manifest_path = os.path.join(base_dir, "app", "src", "main", "AndroidManifest.xml")
with open(manifest_path, "r", encoding="utf-8") as f:
    content = f.read()
content = content.replace('android:name=".StreamHubApp"', 'android:name=".TheDesiTadkaApp"')
with open(manifest_path, "w", encoding="utf-8") as f:
    f.write(content)
print("Updated AndroidManifest.xml")

# 4. In all source files (.kt, .java, .xml, .kts), replace package and class references
exts = (".kt", ".java", ".xml", ".kts")
for root, dirs, files in os.walk(base_dir):
    if any(ignore in root for ignore in ["build", ".gradle", "bin"]):
        continue
    for file in files:
        if file.endswith(exts):
            filepath = os.path.join(root, file)
            with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
                text = f.read()
            
            modified = False
            if "com.streamhub" in text:
                text = text.replace("com.streamhub", "com.thedesitadka")
                modified = True
            if "StreamHubTheme" in text:
                text = text.replace("StreamHubTheme", "TheDesiTadkaTheme")
                modified = True
            if "StreamHubApp" in text:
                text = text.replace("StreamHubApp", "TheDesiTadkaApp")
                modified = True
            
            if modified:
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(text)

print("Replaced package & symbol references in source files")

# 5. Rename StreamHubApp.kt to TheDesiTadkaApp.kt if it exists
old_app_file = os.path.join(base_dir, "app", "src", "main", "java", "com", "streamhub", "app", "StreamHubApp.kt")
new_app_file = os.path.join(base_dir, "app", "src", "main", "java", "com", "streamhub", "app", "TheDesiTadkaApp.kt")
if os.path.exists(old_app_file):
    os.rename(old_app_file, new_app_file)
    print("Renamed StreamHubApp.kt to TheDesiTadkaApp.kt")

# 6. Move com/streamhub to com/thedesitadka across all modules
modules = ["core-model", "core-security", "core-network", "core-config", "provider-engine", "app"]
subtrees = [os.path.join("src", "main", "java"), os.path.join("src", "test", "java")]

for mod in modules:
    for sub in subtrees:
        old_path = os.path.join(base_dir, mod, sub, "com", "streamhub")
        new_path = os.path.join(base_dir, mod, sub, "com", "thedesitadka")
        if os.path.exists(old_path):
            if os.path.exists(new_path):
                shutil.rmtree(new_path)
            os.rename(old_path, new_path)
            print(f"Renamed {old_path} -> {new_path}")

print("Package migration completed successfully.")
