// WScript is a GUI host, so PowerShell is hidden from process creation onward.
var arguments = WScript.Arguments;
if (arguments.length !== 2) WScript.Quit(1);

function quotePath(path) {
    if (!path || /["%\r\n]/.test(path)) throw new Error("Invalid path");
    return '"' + path + '"';
}

try {
    var files = new ActiveXObject("Scripting.FileSystemObject");
    var script = files.BuildPath(files.GetParentFolderName(WScript.ScriptFullName), "Invoke-DailyPostgreSqlBackup.ps1");
    if (!files.FileExists(arguments(0)) || !files.FileExists(script)) {
        WScript.Quit(1);
    }
    quotePath(arguments(0));
    quotePath(script);
    if (arguments(1) === '--check') WScript.Quit(0);
    if (!files.FileExists(arguments(1))) WScript.Quit(1);
    var command = quotePath(arguments(0)) + " -NoProfile -NonInteractive -WindowStyle Hidden -File " +
        quotePath(script) + " -ConfigurationPath " + quotePath(arguments(1)) + " -StartDockerDesktop";
    WScript.Quit(new ActiveXObject("WScript.Shell").Run(command, 0, true));
}
catch (error) {
    WScript.Quit(1);
}
