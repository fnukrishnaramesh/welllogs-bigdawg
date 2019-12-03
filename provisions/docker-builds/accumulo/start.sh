#!/bin/bash

tag=docker.io/sroegner/accumulo
container_name=accumulo-cluster
accumulo_net=accumulo-docker-local

if [ "$1" = "-clean" -o "$1" = "shutdown" ]
then
	echo "Shutting down all Accumulo containers ($1)"
  for c in namenode zookeeper tserver0 tserver1 tserver2 master proxy
  do
    docker rm -f ${container_name}-${c}
  done
fi

if [ "$1" = "shutdown" ]
then
	exit 0
fi

run_container() {
  fqdn="${1}.docker.local"
  /usr/bin/docker run -d --net=${accumulo_net} \
                         --hostname=${fqdn} --net-alias=${fqdn} \
                         -e="SVCLIST=${2}" \
                         --name=${container_name}-${1} ${tag} \
                         /usr/bin/supervisord -n
}

# the cluster requires DNS to work which we use a custom docker network
# with its embedded DNS server for
$(/usr/bin/docker network inspect ${accumulo_net} &>/dev/null) || {
  # TODO: would be nice to check that the local docker client is new enough for this
  /usr/bin/docker network create -d bridge --subnet=172.25.10.0/24 $accumulo_net
}

echo "Welcome to Accumulo on Docker!"
echo
echo "starting hdfs"
run_container namenode 'namenode,datanode'
echo "start zookeeper"
run_container zookeeper 'zookeeper'
echo "hdfs needs a moment to become available"
sleep 10
echo "initializing Accumulo instance"
docker exec ${container_name}-namenode /usr/lib/accumulo/bin/init_accumulo.sh
echo "start tservers"
run_container tserver0 'datanode,accumulo-tserver'
run_container tserver1 'datanode,accumulo-tserver'
run_container tserver2 'datanode,accumulo-tserver'
echo "start accumulo master"
run_container master 'accumulo-master,accumulo-monitor'
echo "start accumulo proxy, gc and tracer"
run_container proxy 'accumulo-tracer,accumulo-gc,accumulo-proxy'
echo "wait for accumulo services to start"
sleep 10
echo create user alfred with password batman
docker exec ${container_name}-tserver0 /tmp/add_user.sh alfred batman
echo

master_ip=$(docker inspect --format '{{(index .NetworkSettings.Networks "accumulo-docker-local").IPAddress}}' ${container_name}-master)
echo "Accumulo Monitor is at http://${master_ip}:50095"
echo -e "Login to accumulo with \n\t docker exec -u accumulo -it ${container_name}-tserver0 /usr/lib/accumulo/bin/accumulo shell -u alfred -p batman"
echo
