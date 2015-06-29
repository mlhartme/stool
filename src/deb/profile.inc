if [ $USER != "root" ] ; then
  export STOOL_OPTS="-Xms128m -Xmx1024m"
  . /opt/ui/opt/tools/stool-3.1/bin/stool-function
  stool reselect
fi
