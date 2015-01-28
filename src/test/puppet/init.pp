$checkout = "https://svn.1and1.org/svn/PFX/wsdtools"

package { "subversion":
  ensure => "installed"
}

package { "openjdk-7-jdk":
  ensure => "installed"
}

file { "/opt/wsdtools":
  ensure => "directory",
  mode => "777",
  require => Exec["getWsdtools"]
}

exec { "getWsdtools" :
  command => "/usr/bin/svn co $checkout /opt/wsdtools",
  path => "/opt/wsdtools",
  require => Package['subversion']
}

file { "/etc/profile.d/wsdtools.sh":
  ensure => present,
  content => ". /opt/wsdtools/profile",
  require => Exec["getWsdtools"]
}