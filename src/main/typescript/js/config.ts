// To create a Client ID see https://docs.microsoft.com/en-us/azure/active-directory/develop/scenario-spa-app-registration
// and submit it to Mojang's review https://help.minecraft.net/hc/en-us/articles/16254801392141p
// You'll need to add the page as a Redirect URL to Azure as a Single Page Application
// noinspection ES6ConvertVarToLetConst - TODO fix this

// Azure Client ID
const azureClientId: string = "a370fff9-7648-4dbf-b96e-2b4f8d539ac2";
// Default CORS Proxy config
var defaultCorsProxy: string | null = "https://cors.re.yt.nom.br/";
// Default instance suffix, in format "viaaas.example.com[:25565]", null to use the page hostname;
var defaultInstanceSuffix: string | null = null;
