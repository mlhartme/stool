class { "apache":
  manage_user   => false,
}

apt::key { 'minibuild' :
  id => 'AECD9D45',
  source    => 'http://buildd-i386.schlund.de:80/~mini-buildd/pgp_key.asc',
}
->
apt::source { 'minibuild':
  location          => 'http://buildd-i386.schlund.de:80/~mini-buildd/rep/',
  release => "wheezy-ui/",
  repos   => '',
}
->
package { 'ui-oracle-java8u40-jdk-no-bc' : }
