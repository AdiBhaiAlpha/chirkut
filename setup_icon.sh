#!/bin/bash
echo "Setting up icon..."
if [ -f "icon.png" ]; then
    echo "Found icon.png in project root."
    rm -f app/src/main/res/drawable/icon.jpg
    rm -f app/src/main/res/drawable/icon.png
    cp icon.png app/src/main/res/drawable/icon.png
    echo "Icon successfully copied to app/src/main/res/drawable/icon.png"
    echo "Please re-run the build to apply changes."
else
    echo "icon.png not found in the project root."
fi
