function [ Wait ] = Wartezeit2(Changes, giveback,period)



Wait=0;
for i=1:size(Changes,1)
    Wait=Wait + Changes(i,6)*mod((giveback(Changes(i,9))-giveback(Changes(i,7))+Changes(i,11)),period);
end

return