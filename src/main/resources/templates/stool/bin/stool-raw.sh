#!/bin/sh
# you'll normally invoke stool-function instead;
# invoke this script if you don't need/want stool changes to the environment
source ${{stool.home}}/bin/stool-function
stool "$@"
