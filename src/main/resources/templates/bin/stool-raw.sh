#! /bin/sh
# Stool dashboard and the service startup invoke this script to execute stool commands with the login environment of the stage owner.
# This script needs sudo permissions.
bash --login -c "source ${{stool.bin}}/stool-function && stool $*"
