using System.IO;
using FTBQuests.Schema;

var output = args.Length > 0
    ? args[0]
    : Path.Combine("tools", "Schemas", "v1_21_1");

output = Path.GetFullPath(output, Directory.GetCurrentDirectory());

var emitter = new SchemaEmitter();
emitter.Emit(output);

Console.WriteLine($"Schemas written to {output}.");
