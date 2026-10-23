import { VideoLibraryPage } from "./VideoLibraryPage";

export function AppGuidePage() {
  return (
    <VideoLibraryPage
      apiPath="/app-guide"
      titleKey="appGuide.title"
      subtitleKey="appGuide.subtitle"
      queryKey="app-guide"
    />
  );
}
