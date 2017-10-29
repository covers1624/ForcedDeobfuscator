ForcedDeobfuscator
==================
This is the most horrible and powerful development tool in existence.


Firstly.
#### DO NOT REPORT BUGS TO MODS OR FORGE WITH THIS INSTALLED!!!
This is explicitly a development tool and has been written as such.
It does a lot of hacky things to the ClassLoader, and FML that should
never exist in any standard environment. IF you use this tool
it is expected that you know what you are doing and that you know how
this tool effects the environment.

So what is it?<br/>
ForcedDeobfuscator is a development tool that forcibly completely deobfuscates
a runtime minecraft environment to the desired MCP mappings. It is
compatible with most coremods that do thing properly by reading gradle args.
Things that use `FMLDeobfuscatingRemapper` currently _might_ not work and this
is probably going to be worked on, but due to the complexity of remapping and
class hierarchy possibly not, depends if i can be bothered.

So why??<br/>
This tool is most useful when attaching your development environment
debugger to a running minecraft instance. Your debugger will have the
correct names for fields, methods and will allow the use of breakpoints,
with the added bonus of your sanity back from not poking MCPBot on IRC
every 3 seconds.

If you would like to use this tool, then <br/>>Insert TODO stuff here.<



###### Disclaimer
This tool is not affiliated with MCP nor is endorsed by MCP.<br/>
This tool does NOT repackage MCP srg or CSV files, these are explicitly,
downloaded from Forge's maven on launch. Again dev tool..


All Rights Reserved
