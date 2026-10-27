# Wayglass
Small open-source Android map app (osmdroid): OpenStreetMap street, Esri satellite imagery, OpenTopoMap, place search (Nominatim) and GPS locate.

## Publish + build the APK on GitHub
    git init -b main && git add . && git commit -m "Wayglass"
    gh repo create wayglass --public --source=. --push
    git tag v1.0 && git push origin v1.0      # Action builds and attaches Wayglass.apk to the release

## Install on your phone (USB or wireless debugging on)
    gh release download v1.0 -p "*.apk" -O Wayglass.apk
    sh install.sh Wayglass.apk                # Windows: install.bat Wayglass.apk

Satellite tiles are Esri World Imagery: fine for personal use, check Esri's terms before distributing widely.
