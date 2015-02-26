$checkout = "https://svn.1and1.org/svn/PFX/wsdtools"

package { "openjdk-7-jdk":
  ensure => "installed"
}

package { "subversion":
  ensure => "installed"
}
