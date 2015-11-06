# TODO: jessie only, for Java packages
# apt::source { 'backports':
#   location          => 'http://http.debian.net/debian',
#   release => "jessie-backports",
#   repos   => 'main',
# }
# ->
apt::source { 'ciso':
  location => 'http://reposrv.loc.lan/1and1/ciso/contrib',
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
