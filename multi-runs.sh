#!/bin/bash

OldIFS="$IFS"
IFS=$'\n' Config=($(<$1))

Runs=${Config[1]}
Jar=${Config[2]}
OutFile=${Config[5]}

IFS=$' \t\r\n'
Classes=$(head -n 1 $1)
Sizes=$(sed -n '4p' < $1)
ThreadNums=$(sed -n '5p' < $1)

IFS="$OldIFS"

Updates=(0 10 20 50 100)


for MinSize in ${Sizes[@]}
do
    MaxSize=$(($MinSize * 2))
    
    for Threads in ${ThreadNums[@]}
    do
	echo "-i $MinSize -r $MaxSize -t $Threads -runs $Runs" >> $OutFile
	echo -ne "update_percent\t" >> $OutFile
	for Class in ${Classes[@]}
	do
	    echo -ne "${Class}\t" >> $OutFile
	done
	echo -ne "\n" >> $OutFile
		
	for U in ${Updates[@]}
	do
	    echo -ne "${U}\t" >> $OutFile
	    for Class in ${Classes[@]}
	    do
		Total=0
		for i in $(seq 1 $Runs)
		do
		    java -jar $Jar -b $Class -i $MinSize -r $MaxSize -t $Threads -u $U | tee tmp.txt
		    Value=`awk '/Throughput/{print $3}' tmp.txt`
		    Value=`echo ${Value} | sed -e 's/[eE]+*/\\*10\\^/'`
		    Total=`echo ${Value} + ${Total} | bc`
		done
		Avg=`echo ${Total} / ${Runs} | bc`
		echo Average: $Avg
		echo -ne "$Avg\t" >> $OutFile
	    done
	    echo -ne "\n" >> $OutFile
	done
    done
done
