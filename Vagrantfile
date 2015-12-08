Vagrant.configure("2") do |config|
  config.vm.box = "ubuntu/trusty64"
  config.vm.box_url = "http://cloud-images.ubuntu.com/vagrant/trusty/current/trusty-server-cloudimg-amd64-vagrant-disk1.box"

  config.vm.define "default" do |devmachine|
    devmachine.vm.network :private_network, ip: "192.168.88.88"
    devmachine.vm.network "forwarded_port", guest: 8000, host: 4040

    config.vm.synced_folder "./vagrant-home/.sbt/", "/home/vagrant/.sbt"
    config.vm.synced_folder "./vagrant-home/.ivy2/", "/home/vagrant/.ivy2"

    devmachine.vm.provider :virtualbox do |v|
      v.name = "geotrellis-workshop"
      v.memory = 2048
      v.cpus = 8
    end
  end
end
