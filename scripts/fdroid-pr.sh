#!/usr/bin/env bash

version_name=$(cat VERSION)
working_dir="/tmp/fdroid-release"
clone_dir="/tmp/fdroid-release/$version_name"
username=$USERNAME
metadata_file="$clone_dir/metadata/im.status.ethereum.yml"
release_link=$RELEASE_LINK
apk_file="$working_dir/$version_name.apk"
commit_sha=$(git rev-parse --verify HEAD)

echo "recreating working directory..."

rm -rf $working_dir
mkdir -p $working_dir

echo "fetching release..."

wget $release_link -O $apk_file

echo "cloning branch, this might take a while..."

git clone git@gitlab.com:$username/fdroiddata.git $clone_dir

echo "updating branch with upstream, this also might take a while..."

cd $clone_dir \
  && git remote add upstream https://gitlab.com/fdroid/fdroiddata.git \
  && git fetch upstream && git checkout master \
  && git reset --hard upstream/master

echo "creating release branch..."

branch_name="release/$version_name"
git checkout -b $branch_name

echo "extracting release code..."

version_code=$(apkanalyzer manifest version-code $apk_file)

# grep last versionName line and extract line number
start_line_raw=$(grep -n "versionName:" $metadata_file | tail -n1 | grep -o -P '([0-9]+):')
start_line=${start_line_raw::-1}

# grep last build line and extract line number
end_line_raw=$(grep -n "build:" $metadata_file | tail -n1 | grep -o -P '([0-9]+):')
end_line=${end_line_raw::-1}

# Get the latest entry, excluding version info
raw_metadata=$(awk "NR >= $(($start_line+3)) && NR <= $end_line" $metadata_file)

# Build new entry
new_entry="  - versionName: $version_name
    versionCode: $version_code
    commit: $commit_sha
"

echo "updating metadata file..."

# Get prelude
start_file=$(awk "NR >= 0 && NR <= $end_line" $metadata_file)
# Get end of file
end_file=$(awk "NR > $end_line" $metadata_file)

# and build output file
printf "$start_file

$new_entry$raw_metadata
$end_file" | head -n -2 > $metadata_file

set_version_entry="CurrentVersion: $version_name
CurrentVersionCode: $version_code"

# append last two lines and remove trailing newline
printf "$set_version_entry" | head -c -1 >> $metadata_file

# Add, commit and push
cd $clone_dir && git add $metadata_file && git commit -m "Add Status version $version_name" && git push --set-upstream origin $branch_name

echo "You can now go to https://gitlab.com/$username/fdroiddata and create PR"
