#!/bin/bash

if [ $# -lt 6 ]; then
    echo "Insufficient arguments: $# "
    echo "./service.sh <SERVICENAME> <IP> <PORT> <NAMESPACE> <USER> <PASSWORD>"
    echo "for example"
    echo "./service.sh db2eventstore-1591210823323 zen-cpd-zen.apps.cpd-001-lb-1.fyre.ibm.com 443 zen xxx xxx"
    exit 1
fi

SERVICENAME=$1
IP=$2
PORT=$3
NAMESPACE=$4
USER=$5
PASSWORD=$6

echo ${IP}:${PORT}

echo 'get bearerToken'
# get bearerToken
bearerToken=`curl -k -X GET https://${IP}:${PORT}/v1/preauth/validateAuth -u ${USER}:${PASSWORD} | jq -r '.accessToken'`

echo $bearerToken
echo '-----------------------'
echo 'get clientkeystore file'

# get clientkeystore file
curl -k -X GET -H "authorization: Bearer $bearerToken" "https://${IP}:${PORT}/icp4data-databases/${SERVICENAME}/${NAMESPACE}/com/ibm/event/api/v1/oltp/keystore" -o clientkeystore

echo '-----------------------'
echo 'get clientkeystore password'
# get clientkeystore password
echo $(curl -k -i -X GET -H "authorization: Bearer $bearerToken" "https://${IP}:${PORT}/icp4data-databases/${SERVICENAME}/${NAMESPACE}/com/ibm/event/api/v1/oltp/keystore_password" | tail -1)


