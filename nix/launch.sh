set -euo pipefail

# The application resolves resources and writes configuration relative to cwd.
runtime_dir="${VOIDMEI_HOME:-${XDG_DATA_HOME:-$HOME/.local/share}/voidmei}"
mkdir -p "$runtime_dir"
cd "$runtime_dir"
for resource in fonts image lang voice; do
    mkdir -p "$resource"
    cp -r --update=none --no-preserve=mode '@assets@/'"$resource"/. "$resource/"
done
cp --update=none --no-preserve=mode '@assets@/ui_layout.cfg' ui_layout.cfg
exec '@java@' -jar '@assets@/VoidMei.jar' "$@"
