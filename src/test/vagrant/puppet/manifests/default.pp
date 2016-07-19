# TODO: jessie only, for Java packages
apt::source { 'backports':
  location => 'http://http.debian.net/debian',
  release => "jessie-backports",
  repos   => 'main',
}
# for ciso tools
apt::source { 'ciso-stable':
  location => 'http://reposrv.loc.lan/1and1/ciso/stable',
  architecture => 'all',
  release => 'jessie',
  repos   => 'contrib',
  key => {
    id     => '1F2860A861A8B825825035753046B215008E6F95',
    source => 'http://reposrv.loc.lan/1and1/ciso/key.asc'
  },
}

# for unstable ciso tools
apt::source { 'ciso-unstable':
  location => 'http://reposrv.loc.lan/1and1/ciso/unstable',
  architecture => 'all',
  release => 'jessie',
  repos   => 'contrib',
  key => {
    id     => '1F2860A861A8B825825035753046B215008E6F95',
    source => 'http://reposrv.loc.lan/1and1/ciso/key.asc'
  },
}
->
Class['apt::update']
->
package { 'openjdk-8-jdk' : }
->
package { 'git' : }
