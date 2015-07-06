#!/bin/sh
name=`pwd`
name=`basename $name`
echo uploading: $name
if [ -d target/checkout ] ; then
  dir=target/checkout/target
else 
  dir=target
fi

scp $dir/$name_*_all.deb ciso@deb-repos.dlan.cinetic.de:/srv/reprepro/incoming-ciso
scp $dir/$name_*_all.changes ciso@deb-repos.dlan.cinetic.de:/srv/reprepro/incoming-ciso

sleep 5

sed -i -- 's/wheezy/squeeze/g' $dir/$name_*_all.changes

scp $dir/$name_*_all.deb ciso@deb-repos.dlan.cinetic.de:/srv/reprepro/incoming-ciso
scp $dir/$name_*_all.changes ciso@deb-repos.dlan.cinetic.de:/srv/reprepro/incoming-ciso

echo done
echo package should show up in http://deb-repos.dlan.cinetic.de/ciso/pool/ 
echo if not:
echo   ssh deb-repos.dlan.cinetic.de
echo   cat /srv/reprepro/logs/incoming-ciso.log
