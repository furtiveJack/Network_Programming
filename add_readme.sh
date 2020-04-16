#!/bin/bash

for tp in $(ls | grep "TP*")
do
	cd $tp
	id=$(echo $tp | sed 's/[^0-9]*//g')
	if [ ${#tp} -eq 1 ]
	then
		id="0${id}"
	fi
	touch README.md
	echo -e "[SUJET](http://www-igm.univ-mlv.fr/~carayol/coursprogreseauINFO2/tds/td${id}.html)" > README.md
	cd ..
done

exit 0
