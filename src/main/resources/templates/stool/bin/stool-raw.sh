#! /bin/sh
# Stool overview invokes this script to execute stool commands with the login environment of the stage owner.
# This script needs sudo permissions.
bash --login -c "source ${{stool.home}}/bin/stool-function && stool $@"
